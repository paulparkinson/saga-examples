package oracle.examples.cloudbank.services;

import oracle.examples.cloudbank.model.Account;
import oracle.examples.cloudbank.model.Journal;
import oracle.examples.cloudbank.repository.AccountRepository;
import oracle.examples.cloudbank.repository.JournalRepository;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.Response;
import java.util.List;


@Component
public class AccountLRAUtils {

    private static AccountLRAUtils singleton;
    final AccountRepository accountRepository;
    final JournalRepository journalRepository;
    public AccountLRAUtils(AccountRepository accountRepository, JournalRepository journalRepository) {
        this.accountRepository = accountRepository;
        this.journalRepository = journalRepository;
        singleton = this;
        System.out.println("LRAUtils accountsRepository = " + accountRepository + ", journalRepository = " + journalRepository);
    }

    public static AccountLRAUtils instance() {
        return singleton;
    }

    public static String getStatusString(ParticipantStatus status) {
        switch (status) {
            case Compensated:
                return "Compensated";
            case Completed:
                return "Completed";
            case FailedToCompensate:
                return "Failed to Compensate";
            case FailedToComplete:
                return "Failed to Complete";
            case Active:
                return "Active";
            case Compensating:
                return "Compensating";
            case Completing:
                return "Completing";
            default:
                return "Unknown";
        }
    }

    public static ParticipantStatus getStatusFromString(String statusString) {
        switch (statusString) {
            case "Compensated":
                return ParticipantStatus.Compensated;
            case "Completed":
                return ParticipantStatus.Completed;
            case "Failed to Compensate":
                return ParticipantStatus.FailedToCompensate;
            case "Failed to Complete":
                return ParticipantStatus.FailedToComplete;
            case "Active":
                return ParticipantStatus.Active;
            case "Compensating":
                return ParticipantStatus.Compensating;
            case "Completing":
                return ParticipantStatus.Completing;
            default:
                return null;
        }
    }

    public void saveAccount(Account account) {
        accountRepository.save(account);
    }

    public  Response status(String lraId) throws Exception {
        Journal journal = getJournalForLRAid(lraId);
        if (AccountLRAUtils.getStatusFromString(journal.getLraState()).equals(ParticipantStatus.Compensated))
            return Response.ok(ParticipantStatus.Compensated).build();
        else return Response.ok(ParticipantStatus.Completed).build();
    }

    public void afterLRA(String lraId, String status) throws Exception {
        Journal journal = getJournalForLRAid(lraId);
        journal.setLraState(status);
        journalRepository.delete(journal);
    }

     Account getAccountForJournal(Journal journal) throws Exception {
        Account account;
        List<Account> accounts = accountRepository.findAccountsByAccountNameContains(journal.getAccountName());
        if (accounts.size() == 0) throw new Exception("Invalid accountName:" + journal.getAccountName());
        account = accounts.get(1);
        return account;
    }
     Account getAccountForAccountName(String accountName)  {
         List<Account> accounts = accountRepository.findAccountsByAccountNameContains(accountName);
         if (accounts.size() == 0) return null;
         return accounts.get(1);
    }

     Journal getJournalForLRAid(String lraId) throws Exception {
        Journal journal;
        List<Journal> journals = journalRepository.findJournalByLraId(lraId);
        if (journals.size() == 0) {
            journalRepository.save(new Journal("unknown", "unknown", 0, lraId,
                    AccountLRAUtils.getStatusString(ParticipantStatus.FailedToComplete)));
            throw new Exception("Journal entry does not exist for lraId:" + lraId);
        }
        journal = journals.get(0);
        return journal;
    }

    public void saveJournal(Journal journal) {
        journalRepository.save(journal);
    }
}
