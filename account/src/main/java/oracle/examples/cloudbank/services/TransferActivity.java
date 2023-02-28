
package oracle.examples.cloudbank.services;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public class TransferActivity {
    private String id;
    private String name;
    private BookingStatus status;
    private String type;
    private TransferActivity[] details;
    private String encodedId;

    private static final Logger log = Logger.getLogger(TransferActivity.class.getSimpleName());

    public TransferActivity() {
    }

    public TransferActivity(String id, String name, String type, TransferActivity... transferActivities) {
        this(id, name, type, BookingStatus.PROVISIONAL, transferActivities);
    }

    public TransferActivity(String id, String name, String type, BookingStatus status, TransferActivity[] details) {
        init(id, name, type, status, details);
    }

    public TransferActivity(TransferActivity transferActivity) {
        this.init(transferActivity.getId(), transferActivity.getName(), transferActivity.getType(), transferActivity.getStatus(), null);
        details = new TransferActivity[transferActivity.getDetails().length];
        IntStream.range(0, details.length).forEach(i -> details[i] = new TransferActivity(transferActivity.getDetails()[i]));
    }

    private void init(String id, String name, String type, BookingStatus status, TransferActivity[] details) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.type = type == null ? "" : type;
        this.status = status;
        try {
            this.encodedId = URLEncoder.encode(id, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.info(e.getLocalizedMessage());
        }
        if(details !=null) {
            this.details = removeNullEnElements(details);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T[] removeNullEnElements(T[] a) {
        List<T> list = new ArrayList<T>(Arrays.asList(a));
        list.removeAll(Collections.singleton(null));
        return list.toArray((T[]) Array.newInstance(a.getClass().getComponentType(), list.size()));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public TransferActivity[] getDetails() {
        return details;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setDetails(TransferActivity[] details) {
        this.details = details;
    }

    public void setEncodedId(String encodedId) {
        this.encodedId = encodedId;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public String getEncodedId() {
        return this.encodedId;
    }


    public boolean merge(TransferActivity transferActivity) {
        if (!id.equals(transferActivity.getId())) {
            return false; // or throw an exception
        }
        this.name = transferActivity.getName();
        this.status = transferActivity.getStatus();
        if(transferActivity.getDetails() != null) {
            for (TransferActivity childTransferActivity : transferActivity.getDetails()) {
                TransferActivity curTransferActivity = Arrays.stream(this.details).filter(a -> a.id.equals(childTransferActivity.id)).findFirst().orElse(null);
                if (curTransferActivity != null) curTransferActivity.merge(childTransferActivity);
            }
        }
        return true;
    }

    public enum BookingStatus {
        CONFIRMED, CANCELLED, PROVISIONAL, CONFIRMING, CANCEL_REQUESTED, FAILED;
    }
}
