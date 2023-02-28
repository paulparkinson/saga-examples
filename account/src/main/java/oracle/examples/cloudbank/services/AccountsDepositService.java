
package oracle.examples.cloudbank.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import oracle.examples.cloudbank.model.Accounts;
import oracle.examples.cloudbank.model.Journal;
import oracle.examples.cloudbank.repository.AccountsRepository;
import oracle.examples.cloudbank.repository.JournalRepository;
import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Logger;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.*;


@RequestScoped
@Path("/account")
@Component
public class AccountsDepositService {
    private static final Logger log = Logger.getLogger(AccountsDepositService.class.getName());

    @Inject
    private AccountLRAState accountLRAState;
    final AccountsRepository accountsRepository;
    final JournalRepository journalRepository;
    private final static String DEPOSIT = "DEPOSIT";

    public AccountsDepositService(AccountsRepository accountsRepository, JournalRepository journalRepository) {
        this.accountsRepository = accountsRepository;
        this.journalRepository = journalRepository;
    }

    /**
     * Write journal entry re deposit amount.
     * Do not increase actual bank account amount
     * @param lraId
     * @param accountName
     * @param depositAmount
     * @return
     */
    @POST
    @Path("/deposit")
    @Produces(MediaType.APPLICATION_JSON)
//    @LRA(value = LRA.Type.MANDATORY, end = false)
    @Transactional
    public Response deposit(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                              @QueryParam("accountName") String accountName,
                            @QueryParam("depositAmount") long depositAmount) throws Exception {
        if (lraId==null) lraId = "testlraId";
        log.info("deposit " + depositAmount + " in account:" + accountName + " (lraId:" + lraId + ")...");
        List<Accounts> accounts = accountsRepository.findAccountsByAccountNameContains(accountName);
        log.info("deposit accounts:" + accounts);
        if (accounts.size() == 0) throw new Exception("Invalid accountName:" + accountName);
        long currentBalance = accounts.get(1).getAccountBalance();
        long newBalance = currentBalance += depositAmount;
        log.info("deposit currentBalance:" + currentBalance + " newBalance:" + newBalance);
        journalRepository.save(new Journal(DEPOSIT, accountName, depositAmount, lraId,
                ParticipantStatusString.getStatusString(ParticipantStatus.Active)));
        TransferActivity transferActivity = lraId == null ? new TransferActivity("bookingId", "flightNumber", "Flight"): accountLRAState.book(lraId, accountName);
        log.info("...deposit " + depositAmount + " in account:" + accountName + " (lraId:" + lraId + ") finished (in pending state)");
        return Response.ok(transferActivity).build();
    }

    /**
     * Increase balance amount as recorded in journal during deposit call.
     * Update LRA state to ParticipantStatus.Completed.
     * @param lraId
     * @return
     * @throws NotFoundException
     * @throws JsonProcessingException
     */
    @PUT
    @Path("/complete")
    @Produces(MediaType.APPLICATION_JSON)
    @Complete
    public Response completeWork(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws Exception {
        log.info("Flight Resource complete() called for LRA : " + lraId);
        List<Journal> journals = journalRepository.findJournalByLraId(lraId);
        if (journals.size() == 0) {
            //todo create entry with FailedToComplete status
            throw new Exception("Journal entry does not exist for lraId:" + lraId);
        }
        Journal journal = journals.get(1);
        if (journal.getJournalType().equals(DEPOSIT)) { //not technically necessary (service only supports one op)
            List<Accounts> accounts = accountsRepository.findAccountsByAccountNameContains(journal.getAccountName());
            if (accounts.size() == 0) throw new Exception("Invalid accountName:" + journal.getAccountName());
            Accounts account = accounts.get(1);
            account.setAccountBalance(account.getAccountBalance() + journal.getJournalAmount());
            accountsRepository.save(account);
            log.info("complete: deposit accounts:" + accounts);
        }
        log.info("journals:" + journals);
        TransferActivity transferActivity = accountLRAState.get(lraId);
        if(transferActivity == null){
            return Response.status(Response.Status.NOT_FOUND).entity("Account booking not found").build();
        }
        if (transferActivity.getStatus() == TransferActivity.BookingStatus.PROVISIONAL) {
            transferActivity.setStatus(TransferActivity.BookingStatus.CONFIRMED);
            return Response.ok(ParticipantStatus.Completed.name()).build();
        }
        transferActivity.setStatus(TransferActivity.BookingStatus.FAILED);
        return Response.ok(ParticipantStatus.FailedToComplete.name()).build();
    }

    /**
     * Update LRA state to ParticipantStatus.Compensated.
     * Do nothing else.
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
        // Business logic to compensate the work related to this LRA
        TransferActivity transferActivity = accountLRAState.get(lraId);
        if(transferActivity == null){
            return Response.status(Response.Status.NOT_FOUND).entity("Account booking not found").build();
        }
        if (transferActivity.getStatus() == TransferActivity.BookingStatus.PROVISIONAL) {
            transferActivity.setStatus(TransferActivity.BookingStatus.CANCELLED);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
        transferActivity.setStatus(TransferActivity.BookingStatus.FAILED);
        return Response.ok(ParticipantStatus.FailedToCompensate.name()).build();
    }

    /**
     *
     * @param lraId
     * @param parentLRA
     * @return
     */
    @GET
    @Path("/status")
    @Produces(MediaType.TEXT_PLAIN)
    @Status
    public Response status(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId, @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) String parentLRA) {
        log.info("Account Resource status() called for LRA : " + lraId);
        if (parentLRA != null) { // is the context nested
            // code which is sensitive to executing with a nested context goes here
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
    @Path("/transfer/{bookingId}")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(LRA.Type.NOT_SUPPORTED)
    public TransferActivity cancelAccount(@PathParam("bookingId") String bookingId) throws URISyntaxException {
        return accountLRAState.cancel(bookingId);
    }

    @GET
    @Path("/transfer/{bookingId}")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(LRA.Type.NOT_SUPPORTED)
    public Response getBooking(@PathParam("bookingId") String bookingId) {
        log.info("Get Account Booking : " + bookingId);
        return Response.ok(accountLRAState.get(bookingId)).build();
    }

    @GET
    @Path("/transfer")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAll() {
        log.info("Get All Account bookings accountsRepository:" + accountsRepository);
        return Response.ok(accountLRAState.getAll()).build();
    }

}
