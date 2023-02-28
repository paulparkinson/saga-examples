
package oracle.examples.cloudbank.services;

import org.springframework.stereotype.Component;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
@Component
public class AccountLRAState {
    //serves as cache only
    private Map<String, TransferActivity> bookings = new HashMap<>();

    public static int MAX_BOOKING = 3;

    public TransferActivity book(String bookingId, String flightNumber) {
        TransferActivity transferActivity = new TransferActivity(bookingId, flightNumber, "Flight");
        if(bookings.size() >= MAX_BOOKING){
            transferActivity.setStatus(TransferActivity.BookingStatus.FAILED);
        }
        TransferActivity earlierTransferActivity = bookings.putIfAbsent(transferActivity.getId(), transferActivity);
        return earlierTransferActivity == null ? transferActivity : earlierTransferActivity;
    }

    public TransferActivity get(String bookingId) throws NotFoundException {
        if (!bookings.containsKey(bookingId))
            throw new NotFoundException(Response.status(404).entity("Invalid bookingId id: " + bookingId).build());
        return bookings.get(bookingId);
    }

    public TransferActivity cancel(String bookingId) throws URISyntaxException {
        TransferActivity transferActivity = get(bookingId);
        transferActivity.setStatus(TransferActivity.BookingStatus.CANCEL_REQUESTED);
        return transferActivity;
    }

    public Collection<TransferActivity> getAll() {
        return bookings.values();
    }
}
