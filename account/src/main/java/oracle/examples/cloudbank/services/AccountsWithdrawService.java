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
    @Transactional
    public Response withdraw(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                            @QueryParam("accountName") String accountName,
                            @QueryParam("amount") long withdrawAmount)  {
        log.info("withdraw " + withdrawAmount + " in account:" + accountName + " (lraId:" + lraId + ")...");
        Account account = AccountLRAUtils.instance().getAccountForAccountName(accountName);
        if (account==null) {
            log.info("withdraw failed: account does not exist");
            return Response.ok("withdraw failed: account does not exist").build();
        }
        if (account.getAccountBalance() < withdrawAmount) {
            log.info("withdraw failed: insufficient funds");
            return Response.ok("withdraw failed: insufficient funds").build();
        }
        account.setAccountBalance(account.getAccountBalance() - withdrawAmount);
        AccountLRAUtils.instance().saveAccount(account);
        AccountLRAUtils.instance().saveJournal(new Journal(WITHDRAW, accountName, withdrawAmount, lraId,
                AccountLRAUtils.getStatusString(ParticipantStatus.Active)));
        return Response.ok("withdraw succeeded").build();
    }

    /**
     * Update LRA state. Do nothing else.
     */
    @PUT
    @Path("/complete")
    @Produces(MediaType.APPLICATION_JSON)
    @Complete
    public Response completeWork(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws NotFoundException {
        log.info("Account withdraw complete() called for LRA : " + lraId);
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
        log.info("Account Resource compensate() called for LRA : " + lraId);
        Journal journal = AccountLRAUtils.instance().getJournalForLRAid(lraId);
        Account account = AccountLRAUtils.instance().getAccountForAccountName(journal.getAccountName());
        journal.setLraState(AccountLRAUtils.getStatusString(ParticipantStatus.Compensating));
        account.setAccountBalance(account.getAccountBalance() + journal.getJournalAmount());
        AccountLRAUtils.instance().saveAccount(account);
        journal.setLraState(AccountLRAUtils.getStatusString(ParticipantStatus.Compensated));
        AccountLRAUtils.instance().saveJournal(journal);
        return Response.ok(ParticipantStatus.Compensated.name()).build();
    }

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
