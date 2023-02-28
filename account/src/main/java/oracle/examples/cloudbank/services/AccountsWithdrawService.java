package oracle.examples.cloudbank.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import oracle.examples.cloudbank.model.Accounts;
import oracle.examples.cloudbank.model.Journal;
import oracle.examples.cloudbank.repository.AccountsRepository;
import oracle.examples.cloudbank.repository.JournalRepository;
import org.eclipse.microprofile.lra.annotation.*;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Logger;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.*;

@RequestScoped
@Path("/account")
@Component
public class AccountsWithdrawService {
    private static final Logger log = Logger.getLogger(AccountsWithdrawService.class.getName());
    public static final String WITHDRAW = "WITHDRAW";

    @Inject
    private AccountLRAState accountLRAState;
    final AccountsRepository accountsRepository;
    final JournalRepository journalRepository;

    public AccountsWithdrawService(AccountsRepository accountsRepository, JournalRepository journalRepository) {
        this.accountsRepository = accountsRepository;
        this.journalRepository = journalRepository;
    }

    /**
     * Reduce account balance by given amount and write journal entry re the same. Both actions in same local tx
     * @param lraId
     * @param accountName
     * @param withdrawAmount
     * @return
     */
    @POST
    @Path("/withdraw")
    @Produces(MediaType.APPLICATION_JSON)
//    @LRA(value = LRA.Type.MANDATORY, end = false)
    @Transactional
    public Response withdraw(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                            @QueryParam("accountName") String accountName,
                            @QueryParam("depositAmount") long withdrawAmount) {
        if (lraId==null) lraId = "testlraId";
        log.info("withdraw accountsRepository:" + accountsRepository);
        log.info("withdraw " + withdrawAmount + " in account:" + accountName + " (lraId:" + lraId + ")...");
        List<Accounts> accounts = accountsRepository.findAccountsByAccountNameContains(accountName);
        log.info("withdraw accounts:" + accounts);
        journalRepository.save(new Journal(WITHDRAW, accountName, withdrawAmount, lraId,
                ParticipantStatusString.getStatusString(ParticipantStatus.Active)));
        List<Journal> journals = journalRepository.findJournalByLraId(lraId);
        log.info("withdraw journals:" + journals);
        TransferActivity transferActivity = accountLRAState.book(lraId, accountName);
        log.info("...withdraw " + withdrawAmount + " in account:" + accountName + " (lraId:" + lraId + ") complete");
        return Response.ok(transferActivity).build();
    }

    /**
     * Update LRA state. Do nothing else.
     * @param lraId
     * @return
     * @throws NotFoundException
     * @throws JsonProcessingException
     */
    @PUT
    @Path("/complete")
    @Produces(MediaType.APPLICATION_JSON)
    @Complete
    public Response completeWork(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws NotFoundException, JsonProcessingException {
        log.info("Account withdraw complete() called for LRA : " + lraId);
        TransferActivity transferActivity = accountLRAState.get(lraId);
        if(transferActivity == null){
            return Response.status(Response.Status.NOT_FOUND).entity("Account withdraw not found").build();
        }
        if (transferActivity.getStatus() == TransferActivity.BookingStatus.PROVISIONAL) {
            transferActivity.setStatus(TransferActivity.BookingStatus.CONFIRMED);
            return Response.ok(ParticipantStatus.Completed.name()).build();
        }
        transferActivity.setStatus(TransferActivity.BookingStatus.FAILED);
        return Response.ok(ParticipantStatus.FailedToComplete.name()).build();
    }

    /**
     * Read the journal and increase the balance by the previous withdraw amount before the LRA
     * @param lraId
     * @return
     * @throws NotFoundException
     * @throws JsonProcessingException
     */
    @PUT
    @Path("/compensate")
    @Produces(MediaType.APPLICATION_JSON)
    @Compensate
    public Response compensateWork(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws NotFoundException, JsonProcessingException {
        log.info("Account Resource compensate() called for LRA : " + lraId);
        TransferActivity transferActivity = accountLRAState.get(lraId);
        if(transferActivity == null){
            return Response.status(Response.Status.NOT_FOUND).entity("Account withdraw not found").build();
        }
        if (transferActivity.getStatus() == TransferActivity.BookingStatus.PROVISIONAL) {
            transferActivity.setStatus(TransferActivity.BookingStatus.CANCELLED);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
        transferActivity.setStatus(TransferActivity.BookingStatus.FAILED);
        return Response.ok(ParticipantStatus.FailedToCompensate.name()).build();
    }

    @GET
    @Path("/status")
    @Produces(MediaType.TEXT_PLAIN)
    @Status
    public Response status(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                           @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) String parentLRA) throws Exception {
        log.info("Account Resource status() called for LRA : " + lraId);
        if (parentLRA != null) { // nested, we are not expecting this in this app
            throw new Exception("Unexpected parent LRA:" + lraId);
        }
        TransferActivity.BookingStatus status = accountLRAState.get(lraId).getStatus();
        // Business logic (provided by the business developer)
        if(status == TransferActivity.BookingStatus.CONFIRMED) {
            return Response.ok(ParticipantStatus.Completed).build();
        }
        return Response.ok(ParticipantStatus.Compensated).build();
    }

    /**
     * Cleanup (delete in this case though archive would be another option) journal entry for LRA
     * @param lraId
     * @param status
     * @return
     * @throws NotFoundException
     */
    @PUT
    @Path("/after")
    @AfterLRA
    @Consumes(MediaType.TEXT_PLAIN)
    public Response afterLRA(@HeaderParam(LRA_HTTP_ENDED_CONTEXT_HEADER) String lraId, String status) throws NotFoundException {
        log.info("After LRA Called : " + lraId);
        log.info("Final LRA Status : " + status);
        // Clean up of resources held by this LRA
        return Response.ok().build();
    }

    @DELETE
    @Path("/transfer/{withdrawId}")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(LRA.Type.NOT_SUPPORTED)
    public TransferActivity cancelWithdraw(@PathParam("withdrawId") String withdrawId) throws URISyntaxException {
        return accountLRAState.cancel(withdrawId);
    }

    @GET
    @Path("/transfer/{withdrawId}")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(LRA.Type.NOT_SUPPORTED)
    public Response getBooking(@PathParam("withdrawId") String withdrawId) {
        log.info("Get Account Booking : " + withdrawId);
        return Response.ok(accountLRAState.get(withdrawId)).build();
    }

    @GET
    @Path("/transfer")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAll() {
        log.info("Get All Account withdraws accountsRepository:" + accountsRepository);
        return Response.ok(accountLRAState.getAll()).build();
    }

}
