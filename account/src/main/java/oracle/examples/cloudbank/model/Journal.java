package oracle.examples.cloudbank.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

/**
 * Journal services multiple purposes:
 *  - Ledger/journal for tracking changes made in an LRA
 *  - Store for LRA state
 *  - Blockchain could facilitate auditing/ledger aspects of the journal
 */
@Entity
@Table(name = "JOURNAL")
@Data
@NoArgsConstructor
public class Journal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "JOURNAL_ID")
    private long journalId;

    /**
     * Eg withdraw or deposit
     */
    @Column(name = "JOURNAL_TYPE")
    private String journalType;

    @Column(name = "ACCOUNT_NAME")
    private String accountName;

    @Column(name = "LRA_ID")
    private String lraId;

    @Column(name = "LRA_STATE")
    private String lraState;

    @Column(name = "JOURNAL_AMOUNT")
    private long journalAmount;

    public Journal(String journalType, String accountName, long journalAmount, String lraId, String lraState) {
        this.journalType = journalType;
        this.accountName = accountName;
        this.lraId = lraId;
        this.lraState = lraState;
        this.journalAmount = journalAmount;
    }
}
