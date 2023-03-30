package oracle.examples.cloudbank.services;

import oracle.examples.cloudbank.model.Account;
import oracle.examples.cloudbank.model.Journal;
import org.eclipse.microprofile.lra.annotation.*;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.enterprise.context.RequestScoped;
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
@Path("/deposit")
@Component
public class AccountsDepositService {
    private static final Logger log = Logger.getLogger(AccountsDepositService.class.getName());

    private final static String DEPOSIT = "DEPOSIT";

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
                              @QueryParam("accountId") long accountId,
                            @QueryParam("amount") long depositAmount) {
        log.info("...deposit " + depositAmount + " in account:" + accountId +
                " (lraId:" + lraId + ") finished (in pending state)");
        Account account = AccountTransferDAO.instance().getAccountForAccountId(accountId);
        if (account==null) {
            log.info("withdraw failed: account does not exist");
            AccountTransferDAO.instance().saveJournal(new Journal(DEPOSIT, accountId, 0, lraId,
                    AccountTransferDAO.getStatusString(ParticipantStatus.Active)));
            return Response.ok("deposit failed: account does not exist").build();
        }
        AccountTransferDAO.instance().saveJournal(new Journal(DEPOSIT, accountId, depositAmount, lraId,
                AccountTransferDAO.getStatusString(ParticipantStatus.Active)));
        return Response.ok("deposit succeeded").build();
    }

    /**
     * Increase balance amount as recorded in journal during deposit call.
     * Update LRA state to ParticipantStatus.Completed.
     */
    @PUT
    @Path("/complete")
    @Produces(MediaType.APPLICATION_JSON)
    @Complete
    @Transactional
    public Response completeWork(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws Exception {
        log.info("deposit complete called for LRA : " + lraId);
        Journal journal = AccountTransferDAO.instance().getJournalForLRAid(lraId, DEPOSIT);
        String lraState = journal.getLraState();
        if(lraState.equals(ParticipantStatus.Compensating) ||
                lraState.equals(ParticipantStatus.Compensated))
            return Response.ok(AccountTransferDAO.getStatusFromString(lraState)).build();
        journal.setLraState(AccountTransferDAO.getStatusString(ParticipantStatus.Completing));
        AccountTransferDAO.instance().saveJournal(journal);
        doCompleteWork(journal);
        return Response.ok(ParticipantStatus.Completed.name()).build();
    }

    @Transactional
    private void doCompleteWork(Journal journal) throws Exception {
        Account account = AccountTransferDAO.instance().getAccountForAccountId(journal.getAccountId());
        if (account != null) {
            account.setAccountBalance(account.getAccountBalance() + journal.getJournalAmount());
            AccountTransferDAO.instance().saveAccount(account);
        } else {
            journal.setLraState(AccountTransferDAO.getStatusString(ParticipantStatus.FailedToComplete));
        }
        journal.setLraState(AccountTransferDAO.getStatusString(ParticipantStatus.Completed));
        AccountTransferDAO.instance().saveJournal(journal);
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
        Journal journal = AccountTransferDAO.instance().getJournalForLRAid(lraId, DEPOSIT);
        journal.setLraState(AccountTransferDAO.getStatusString(ParticipantStatus.Compensated));
        AccountTransferDAO.instance().saveJournal(journal);
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
        return AccountTransferDAO.instance().status(lraId, DEPOSIT);
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
        AccountTransferDAO.instance().afterLRA(lraId, status, DEPOSIT);
        return Response.ok().build();
    }

}
