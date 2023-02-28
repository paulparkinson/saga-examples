/*

 Oracle Transaction Manager for Microservices
 
 Copyright © 2022, Oracle and/or its affiliates. All rights reserved.

 This software and related documentation are provided under a license agreement containing restrictions on use and disclosure and are protected by intellectual property laws. Except as expressly permitted in your license agreement or allowed by law, you may not use, copy, reproduce, translate, broadcast, modify, license, transmit, distribute, exhibit, perform, publish, or display any part, in any form, or by any means. Reverse engineering, disassembly, or decompilation of this software, unless required by law for interoperability, is prohibited.

 The information contained herein is subject to change without notice and is not warranted to be error-free. If you find any errors, please report them to us in writing.

 If this is software or related documentation that is delivered to the U.S. Government or anyone licensing it on behalf of the U.S. Government, then the following notice is applicable:

 U.S. GOVERNMENT END USERS: Oracle programs, including any operating system, integrated software, any programs installed on the hardware, and/or documentation, delivered to U.S. Government end users are "commercial computer software" pursuant to the applicable Federal Acquisition Regulation and agency-specific supplemental regulations. As such, use, duplication, disclosure, modification, and adaptation of the programs, including any operating system, integrated software, any programs installed on the hardware, and/or documentation, shall be subject to license terms and license restrictions applicable to the programs. No other rights are granted to the U.S. Government.

 This software or hardware is developed for general use in a variety of information management applications. It is not developed or intended for use in any inherently dangerous applications, including applications that may create a risk of personal injury. If you use this software or hardware in dangerous applications, then you shall be responsible to take all appropriate fail-safe, backup, redundancy, and other measures to ensure its safe use. Oracle Corporation and its affiliates disclaim any liability for any damages caused by use of this software or hardware in dangerous applications.
 Oracle and Java are registered trademarks of Oracle and/or its affiliates. Other names may be trademarks of their respective owners.
 Intel and Intel Xeon are trademarks or registered trademarks of Intel Corporation. All SPARC trademarks are used under license and are trademarks or registered trademarks of SPARC International, Inc. AMD, Opteron, the AMD logo, and the AMD Opteron logo are trademarks or registered trademarks of Advanced Micro Devices. UNIX is a registered trademark of The Open Group.

 This software or hardware and documentation may provide access to or information about content, products, and services from third parties. Oracle Corporation and its affiliates are not responsible for and expressly disclaim all warranties of any kind with respect to third-party content, products, and services unless otherwise set forth in an applicable agreement between you and Oracle. Oracle Corporation and its affiliates will not be responsible for any loss, costs, or damages incurred due to your access to or use of third-party content, products, or services, except as set forth in an applicable agreement between you and Oracle.

*/
package com.oracle.trm.lra.demo.tripservice;


import com.oracle.trm.lra.demo.model.Booking;
import io.helidon.lra.coordinator.client.PropagatedHeaders;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
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
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.logging.Logger;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_ENDED_CONTEXT_HEADER;

@ApplicationScoped
@Path("/trip-service/api")
public class TripManagerResource {

    private static final String ORACLE_TMM_TX_TOKEN = "Oracle-Tmm-Tx-Token";
    private static final Logger log = Logger.getLogger(TripManagerResource.class.getSimpleName());
    private URI hotelResUri;
    private URI flightResUri;

    @Inject
    private TripService service;

    @Inject
    @ConfigProperty(name = "hotel.service.url")
    private String hotelRes;

    @Inject
    @ConfigProperty(name = "flight.service.url")
    private String flightRes;

    @Inject
    @ConfigProperty(name = "mp.lra.coordinator.url")
    private String coordinatorRes;


    /**
     * Initialise the hotel and flight service endpoints
     */
    @PostConstruct
    private void initController() {
        try {
            hotelResUri = new URI(hotelRes);
            flightResUri = new URI(flightRes);
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("Failed to initialize " + TripManagerResource.class.getName(), ex);
        }
    }

    @POST
    @Path("/trip")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.REQUIRES_NEW, end = false)
    //end is false so that the LRA remains active after the method is finished execution. LRA will be closed or compensated only after the booking is confirmed or cancelled
    public Response bookTrip(@QueryParam("hotelName") @DefaultValue("TheGrand") String hotelName, @QueryParam("flightNumber") @DefaultValue("A123") String flightNumber, @Context UriInfo uriInfo, @HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId, @Context ContainerRequestContext containerRequestContext) throws BookingException, UnsupportedEncodingException, UnknownHostException {
        if (lraId == null) {
            return Response.serverError().entity("Failed to create LRA").build();
        }
        log.info("Started new LRA : " + lraId);

        Booking tripBooking = null;
        try {
            Booking flightBooking = null;
            Booking hotelBooking = bookHotel(hotelName, lraId);
            // Book the flight only when hotel booking did not fail
            if (hotelBooking.getStatus() != Booking.BookingStatus.FAILED) {
                flightBooking = bookFlight(flightNumber, lraId);
            }
            // Create trip booking that contains the hotel and flight bookings
            tripBooking = new Booking(lraId, "Trip", "Trip", hotelBooking, flightBooking);
            service.saveProvisionalBooking(tripBooking);
            return Response.ok(tripBooking).header(ORACLE_TMM_TX_TOKEN, getOracleTmmTxToken(containerRequestContext)).build();
        } catch (BookingException ex) {
            return Response.status(INTERNAL_SERVER_ERROR.getStatusCode(), ex.getLocalizedMessage()).entity(tripBooking).build();
        }
    }

    @GET
    @Path("/trip/{bookingId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getBooking(@PathParam("bookingId") String bookingId) {
        return Response.ok(service.get(bookingId)).build();
    }

    @PUT
    @Path("/trip/{bookingId}")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.MANDATORY, end = true)
    public Response confirmTrip(@PathParam("bookingId") String bookingId) throws NotFoundException {
        log.info("Received Confirmation for trip booking with Id : " + bookingId);
        Booking tripBooking = service.get(bookingId);
        if (tripBooking.getStatus() == Booking.BookingStatus.CANCEL_REQUESTED)
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity("Cannot confirm a trip booking that needs to be cancelled").build());
        return Response.ok().build();
    }

    @DELETE
    @Path("/trip/{bookingId}")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.MANDATORY, end = true, cancelOn = Response.Status.OK)
    public Response cancelTrip(@PathParam("bookingId") String bookingId) throws NotFoundException {
        log.info("Received Cancellation for trip booking with Id : " + bookingId);
        return Response.ok("Cancel booking requested").build();
    }

    @GET
    @Path("/trip")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAll() {
        return Response.ok(service.getAll()).build();
    }

    @PUT
    @Path("/after")
    @AfterLRA
    @Consumes(MediaType.TEXT_PLAIN)
    public Response afterLRA(@HeaderParam(LRA_HTTP_ENDED_CONTEXT_HEADER) URI lraId, LRAStatus status) throws NotFoundException {
        log.info("After LRA Called : " + lraId);
        log.info("Final LRA Status : " + status);
        Booking tripBooking = service.get(lraId.toString());
        if (tripBooking != null) {
            // Fetch the final status of hotel and flight booking
            service.mergeAssociateBookingDetails(tripBooking, getHotelTarget(), getFlightTarget());
        }
        // Clean up of resources held by this LRA
        return Response.ok().build();
    }

    /**
     * Calls the hotel service to book a room at the given hotel
     *
     * @param name Name of the hotel to be booked
     * @param id   Identity of the booking
     * @return hotel booking details
     */

    private Booking bookHotel(String name, String id) {
        log.info("Calling Hotel Service to book hotel with booking Id : " + id);
        WebTarget webTarget = getHotelTarget().path("/").queryParam("hotelName", name);
        Booking hotelBooking = webTarget.request().post(Entity.text("")).readEntity(Booking.class);
        log.info(String.format("Hotel booking %s with booking Id : %s", (hotelBooking.getStatus() == Booking.BookingStatus.FAILED ? "FAILED" : "SUCCESSFUL"), hotelBooking.getId()));
        return hotelBooking;
    }

    /**
     * Calls the flight service to book a seat at the given flight
     *
     * @param flightNumber Name of the flight to be booked
     * @param id           Identity of the booking
     * @return flight booking details
     */
    private Booking bookFlight(String flightNumber, String id) {
        log.info("Calling Flight Service to book flight with booking Id : " + id);
        WebTarget webTarget = getFlightTarget().path("/").queryParam("flightNumber", flightNumber);
        Booking flightBooking = webTarget.request().post(Entity.text("")).readEntity(Booking.class);
        log.info(String.format("Flight booking %s with booking Id : %s", (flightBooking.getStatus() == Booking.BookingStatus.FAILED ? "FAILED" : "SUCCESSFUL"), flightBooking.getId()));
        return flightBooking;
    }

    private WebTarget getHotelTarget() {
        return ClientBuilder.newClient().target(hotelResUri);
    }

    private WebTarget getFlightTarget() {
        return ClientBuilder.newClient().target(flightResUri);
    }

    private String getOracleTmmTxToken(ContainerRequestContext reqContext) {
        PropagatedHeaders propagatedHeaders = (PropagatedHeaders) reqContext.getProperty(PropagatedHeaders.class.getName());
        List<String> headerValue = propagatedHeaders.toMap().getOrDefault(ORACLE_TMM_TX_TOKEN, null);
        return headerValue != null && headerValue.size() > 0 ? headerValue.get(0) : null;
    }
}
