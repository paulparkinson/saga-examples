package oracle.examples.cloudbank.services;

import oracle.examples.cloudbank.model.Account;
import oracle.examples.cloudbank.model.Journal;
import oracle.examples.cloudbank.repository.AccountRepository;
import oracle.examples.cloudbank.repository.JournalRepository;
import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.enterprise.context.RequestScoped;
//import javax.enterprise.context.RequestScoped;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.logging.Logger;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.*;


@RequestScoped
@Path("/account")
@Component
public class AccountsDepositService {
    private static final Logger log = Logger.getLogger(AccountsDepositService.class.getName());

    final AccountRepository accountRepository;
    final JournalRepository journalRepository;
    private final static String DEPOSIT = "DEPOSIT";

    public AccountsDepositService(AccountRepository accountRepository, JournalRepository journalRepository) {
        this.accountRepository = accountRepository;
        this.journalRepository = journalRepository;
    }

    /**
     * Write journal entry re deposit amount.
     * Do not increase actual bank account amount
     */
    @POST
    @Path("/deposit")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.MANDATORY, end = false)
    @Transactional
    public Response deposit(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                              @QueryParam("accountName") String accountName,
                            @QueryParam("depositAmount") long depositAmount) throws Exception {
        journalRepository.save(new Journal(DEPOSIT, accountName, depositAmount, lraId,
                AccountLRAUtils.getStatusString(ParticipantStatus.Active)));
        log.info("...deposit " + depositAmount + " in account:" + accountName +
                " (lraId:" + lraId + ") finished (in pending state)");
        return Response.ok("deposit successful").build();
    }

    /**
     * Increase balance amount as recorded in journal during deposit call.
     * Update LRA state to ParticipantStatus.Completed.
     */
    @PUT
    @Path("/complete")
    @Produces(MediaType.APPLICATION_JSON)
    @Complete
    public Response completeWork(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws Exception {
        log.info("deposit complete called for LRA : " + lraId);
        //get the journal and account...
        Journal journal = AccountLRAUtils.instance().getJournalForLRAid(lraId);
        Account account= AccountLRAUtils.instance().getAccountForJournal(journal);
        journal.setLraState(AccountLRAUtils.getStatusString(ParticipantStatus.Completing));
        //update the account balance and journal entry...
        account.setAccountBalance(account.getAccountBalance() + journal.getJournalAmount());
        accountRepository.save(account);
        journal.setLraState(AccountLRAUtils.getStatusString(ParticipantStatus.Completed));
        journalRepository.save(journal);
        return Response.ok(ParticipantStatus.Completed.name()).build();
    }

    /**
     * Update LRA state to ParticipantStatus.Compensated.
     */
    @PUT
    @Path("/compensate")
    @Produces(MediaType.APPLICATION_JSON)
    @Compensate
    public Response compensateWork(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws Exception {
        log.info("deposit compensate called for LRA : " + lraId);
        Journal journal = AccountLRAUtils.instance().getJournalForLRAid(lraId);
        journal.setLraState(AccountLRAUtils.getStatusString(ParticipantStatus.Compensated));
        journalRepository.save(journal);
        return Response.ok(ParticipantStatus.Compensated.name()).build();
    }

    /**
     * Return status
     */
    @GET
    @Path("/status")
    @Produces(MediaType.TEXT_PLAIN)
    @Status
    public Response status(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                           @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) String parentLRA) throws Exception {
        return AccountLRAUtils.instance().status(lraId);
    }

    /**
     * Delete journal entry for LRA
     */
    @PUT
    @Path("/after")
    @AfterLRA
    @Consumes(MediaType.TEXT_PLAIN)
    public Response afterLRA(@HeaderParam(LRA_HTTP_ENDED_CONTEXT_HEADER) String lraId, String status) throws Exception {
        log.info("After LRA Called : " + lraId);
        AccountLRAUtils.instance().afterLRA(lraId, status);
        return Response.ok().build();
    }

}
