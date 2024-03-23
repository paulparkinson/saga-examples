package oracle.examples.cloudbank;

import io.narayana.lra.Current;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;

import javax.ws.rs.*;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

@ApplicationScoped
@Path("/")
public class TransferService {

    private static final Logger log = Logger.getLogger(TransferService.class.getSimpleName());
    public static final String TRANSFER_ID = "TRANSFER_ID";
    private URI withdrawUri;
    private URI depositUri;
    private URI transferCancelUri;
    private URI transferConfirmUri;
    private URI transferProcessCancelUri;
    private URI transferProcessConfirmUri;


    @PostConstruct
    private void initController() {
        try {
            withdrawUri = new URI(ApplicationConfig.accountWithdrawUrl);
            depositUri = new URI(ApplicationConfig.accountDepositUrl);
            transferCancelUri = new URI(ApplicationConfig.transferCancelURL);
            transferConfirmUri = new URI(ApplicationConfig.transferConfirmURL);
            transferProcessCancelUri = new URI(ApplicationConfig.transferCancelProcessURL);
            transferProcessConfirmUri = new URI(ApplicationConfig.transferConfirmProcessURL);
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("Failed to initialize " + TransferService.class.getName(), ex);
        }
    }

    @POST
    @Path("/transfer")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.REQUIRES_NEW, end = false)
    public Response transfer(@QueryParam("fromAccount") long fromAccount,
                             @QueryParam("toAccount") long toAccount,
                             @QueryParam("amount") long amount,
                             @HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId)
    {
        if (lraId == null) {
            return Response.serverError().entity("Failed to create LRA").build();
        }
        log.info("Started new LRA/transfer Id: " + lraId);
        boolean isCompensate = false;
        String returnString = "";
        returnString += withdraw(fromAccount, amount);
        log.info(returnString);
        if (returnString.contains("succeeded")) {
            returnString += " " + deposit(toAccount, amount);
            log.info(returnString);
            if (returnString.contains("failed")) isCompensate = true; //deposit failed
        } else isCompensate = true; //withdraw failed
        log.info("LRA/transfer action will be " + (isCompensate?"cancel":"close"));
        WebTarget webTarget = ClientBuilder.newClient().target(isCompensate?transferCancelUri:transferConfirmUri);
        webTarget.request().header(TRANSFER_ID, lraId)
                .post(Entity.text("")).readEntity(String.class);
        return Response.ok("transfer status:" + returnString).build();

    }

    private String withdraw(long accountId, long amount) {
        log.info("withdraw accountId = " + accountId + ", amount = " + amount);
        WebTarget webTarget =
                ClientBuilder.newClient().target(withdrawUri).path("/")
                        .queryParam("accountId", accountId)
                        .queryParam("amount", amount);
        URI lraId = Current.peek();
        log.info("withdraw lraId = " + lraId);
        String withdrawOutcome =
                webTarget.request().header(LRA_HTTP_CONTEXT_HEADER,lraId)
                        .post(Entity.text("")).readEntity(String.class);
        return withdrawOutcome;
    }
    private String deposit(long accountId, long amount) {
        log.info("deposit accountId = " + accountId + ", amount = " + amount);
        WebTarget webTarget =
                ClientBuilder.newClient().target(depositUri).path("/")
                        .queryParam("accountId", accountId)
                        .queryParam("amount", amount);
        URI lraId = Current.peek();
        log.info("deposit lraId = " + lraId);
        String depositOutcome =
                webTarget.request().header(LRA_HTTP_CONTEXT_HEADER,lraId)
                        .post(Entity.text("")).readEntity(String.class);;
        return depositOutcome;
    }




    @POST
    @Path("/processclose")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.MANDATORY)
    public Response processClose(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws NotFoundException {
        log.info("Process close for transfer : " + lraId);
        return Response.ok().build();
    }

    @POST
    @Path("/processcancel")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.MANDATORY, cancelOn = Response.Status.OK)
    public Response processCancel(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws NotFoundException {
        log.info("Process cancel for transfer : " + lraId);
        return Response.ok().build();
    }


    // The following two methods could be in an external client.
    // They are included here for convenience.
    // The transfer method makes a Rest call to close or commit.
    // The close or commit method suspends the LRA (via NOT_SUPPORTED)
    // The close or commit method then proceeds to make a Rest call to the "processclose" or "processcommit" method
    // The "processclose" and "processcommit" methods import the LRA (via MANDATORY)
    //  and end the LRA implicitly accordingly upon return.
    @POST
    @Path("/close")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.NOT_SUPPORTED)
    public Response close(@HeaderParam(TRANSFER_ID) String transferId) throws NotFoundException {
        log.info("Received close for transfer : " + transferId);
        String closeOutcome =
                ClientBuilder.newClient().target(transferProcessConfirmUri).request()
                        .header(LRA_HTTP_CONTEXT_HEADER, transferId)
                        .post(Entity.text("")).readEntity(String.class);
        return Response.ok(closeOutcome).build();
    }

    @POST
    @Path("/cancel")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.NOT_SUPPORTED, cancelOn = Response.Status.OK)
    public Response cancel(@HeaderParam(TRANSFER_ID) String transferId) throws NotFoundException {
        log.info("Received cancel for transfer : " + transferId);
        String closeOutcome =
                ClientBuilder.newClient().target(transferProcessCancelUri).request()
                        .header(LRA_HTTP_CONTEXT_HEADER, transferId)
                        .post(Entity.text("")).readEntity(String.class);
        return Response.ok(closeOutcome).build();
    }

}
