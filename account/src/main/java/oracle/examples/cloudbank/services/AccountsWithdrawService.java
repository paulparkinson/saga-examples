package oracle.examples.cloudbank.services;

import oracle.examples.cloudbank.model.Account;
import oracle.examples.cloudbank.model.Journal;
import org.eclipse.microprofile.lra.annotation.*;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.logging.Logger;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.*;

@RequestScoped
@Path("/withdraw")
@Component
public class AccountsWithdrawService {
    private static final Logger log = Logger.getLogger(AccountsWithdrawService.class.getName());
    public static final String WITHDRAW = "WITHDRAW";

    /**
     * Reduce account balance by given amount and write journal entry re the same. Both actions in same local tx
     */
    @POST
    @Path("/withdraw")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.MANDATORY, end = false)
    public Response withdraw(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                            @QueryParam("accountId") long accountId,
                            @QueryParam("amount") long withdrawAmount)  {
        log.info("withdraw " + withdrawAmount + " in account:" + accountId + " (lraId:" + lraId + ")...");
        Account account = AccountTransferDAO.instance().getAccountForAccountId(accountId);
        if (account==null) {
            log.info("withdraw failed: account does not exist"); //could also do leave here
            AccountTransferDAO.instance().saveJournal(new Journal(WITHDRAW, accountId, 0, lraId,
                    AccountTransferDAO.getStatusString(ParticipantStatus.Active)));
            return Response.ok("withdraw failed: account does not exist").build();
        }
        if (account.getAccountBalance() < withdrawAmount) {
            log.info("withdraw failed: insufficient funds");
            AccountTransferDAO.instance().saveJournal(new Journal(WITHDRAW, accountId, 0, lraId,
                    AccountTransferDAO.getStatusString(ParticipantStatus.Active)));
            return Response.ok("withdraw failed: insufficient funds").build();
        }
        log.info("withdraw current balance:" + account.getAccountBalance() +
                " new balance:" + (account.getAccountBalance() - withdrawAmount));
        updateAccountBalance(lraId, accountId, withdrawAmount, account);
        return Response.ok("withdraw succeeded").build();
    }


    @Transactional
    private void updateAccountBalance(String lraId, long accountId, long withdrawAmount, Account account) {
        account.setAccountBalance(account.getAccountBalance() - withdrawAmount);
        AccountTransferDAO.instance().saveAccount(account);
        AccountTransferDAO.instance().saveJournal(new Journal(WITHDRAW, accountId, withdrawAmount, lraId,
                AccountTransferDAO.getStatusString(ParticipantStatus.Active)));
    }

    /**
     * Update LRA state. Do nothing else.
     */
    @PUT
    @Path("/complete")
    @Produces(MediaType.APPLICATION_JSON)
    @Complete
    public Response completeWork(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws Exception {
        log.info("withdraw complete called for LRA : " + lraId);
        Journal journal = AccountTransferDAO.instance().getJournalForLRAid(lraId, WITHDRAW);
        if (journal != null) {
            journal.setLraState(AccountTransferDAO.getStatusString(ParticipantStatus.Completed));
        } else journal.setLraState(AccountTransferDAO.getStatusString(ParticipantStatus.FailedToComplete));
        AccountTransferDAO.instance().saveJournal(journal);
        return Response.ok(ParticipantStatus.Completed.name()).build();
    }

    /**
     * Read the journal and increase the balance by the previous withdraw amount before the LRA
     */
    @PUT
    @Path("/compensate")
    @Produces(MediaType.APPLICATION_JSON)
    @Compensate
    public Response compensateWork(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws Exception {
        log.info("Account withdraw compensate() called for LRA : " + lraId);
        Journal journal = AccountTransferDAO.instance().getJournalForLRAid(lraId, WITHDRAW);
        String lraState = journal.getLraState();
        if(lraState.equals(ParticipantStatus.Compensating) ||
                lraState.equals(ParticipantStatus.Compensated))
            return Response.ok(AccountTransferDAO.getStatusFromString(lraState)).build();
        journal.setLraState(AccountTransferDAO.getStatusString(ParticipantStatus.Compensating));
        AccountTransferDAO.instance().saveJournal(journal);
        doCompensationWork(journal);
        return Response.ok(ParticipantStatus.Compensated.name()).build();
    }

    @Transactional
    private void doCompensationWork(Journal journal) {
        Account account = AccountTransferDAO.instance().getAccountForAccountId(journal.getAccountId());
        if (account != null) {
            account.setAccountBalance(account.getAccountBalance() + journal.getJournalAmount());
            AccountTransferDAO.instance().saveAccount(account);
        } else {
            journal.setLraState(AccountTransferDAO.getStatusString(ParticipantStatus.FailedToCompensate));
        }
        journal.setLraState(AccountTransferDAO.getStatusString(ParticipantStatus.Compensated));
        AccountTransferDAO.instance().saveJournal(journal);
    }

    @GET
    @Path("/status")
    @Produces(MediaType.TEXT_PLAIN)
    @Status
    public Response status(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                           @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) String parentLRA) throws Exception {
        return AccountTransferDAO.instance().status(lraId, WITHDRAW);
    }

    /**
     * Delete journal entry for LRA (or keep for auditing)
     */
    @PUT
    @Path("/after")
    @AfterLRA
    @Consumes(MediaType.TEXT_PLAIN)
    public Response afterLRA(@HeaderParam(LRA_HTTP_ENDED_CONTEXT_HEADER) String lraId, LRAStatus status) throws Exception {
        log.info("After LRA Called : " + lraId);
        AccountTransferDAO.instance().afterLRA(lraId, status, WITHDRAW);
        return Response.ok().build();
    }

}
