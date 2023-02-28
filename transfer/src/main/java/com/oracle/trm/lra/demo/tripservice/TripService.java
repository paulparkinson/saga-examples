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

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

@ApplicationScoped
public class TripService {

    private static final Logger log = Logger.getLogger(TripService.class.getSimpleName());
    private Map<String, Booking> bookings = new HashMap<>();

    /**
     * Save the trip booking in memory (HashMap)
     *
     * @param booking - Trip booking
     * @throws BookingException Exception is throw if one of the associate bookings failed
     */
    public void saveProvisionalBooking(Booking booking) throws BookingException {
        bookings.putIfAbsent(booking.getId(), booking);

        //check if any associate booking is a failed booking
        for (Booking associatedBooking : booking.getDetails()) {
            if (associatedBooking.getStatus() == Booking.BookingStatus.FAILED) {
                //cancel the LRA by throwing an exception
                log.info(String.format("Cancelling booking id %s (%s) status: %s", booking.getId(), booking.getName(), booking.getStatus()));
                booking.setStatus(Booking.BookingStatus.CANCELLED);
                throw new BookingException(INTERNAL_SERVER_ERROR.getStatusCode(), String.format("Associate booking failed: %s", booking.getName()));
            }
        }
    }

    /**
     * Get trip booking details
     *
     * @param bookingId booking identity
     * @return Booking details
     * @throws NotFoundException
     */
    public Booking get(String bookingId) throws NotFoundException {
        if (!bookings.containsKey(bookingId)) {
            throw new NotFoundException(Response.status(404).entity("Invalid Booking Id: " + bookingId).build());
        }
        return bookings.get(bookingId);
    }

    /**
     * Fetch all trip bookings
     *
     * @return all trip booking details
     */
    public Collection<Booking> getAll() {
        return bookings.values();
    }

    /**
     * Fetch associated booking details and confirm the trip booking status
     *
     * @param tripBooking  Trip booking details
     * @param hotelTarget  Hotel service web target
     * @param flightTarget Flight Service web target
     */
    public void mergeAssociateBookingDetails(Booking tripBooking, WebTarget hotelTarget, WebTarget flightTarget) {
        for (Booking associatedBooking : tripBooking.getDetails()) {
            if ("Hotel".equals(associatedBooking.getType())) {
                mergeAssociateBookingDetails(hotelTarget, associatedBooking);
            } else if ("Flight".equals(associatedBooking.getType())) {
                mergeAssociateBookingDetails(flightTarget, associatedBooking);
            }
        }
        // If any associate booking fails, the entire trip fails
        boolean anyAssociatedBookingFailed = Arrays.stream(tripBooking.getDetails()).anyMatch(booking -> booking.getStatus().equals(Booking.BookingStatus.FAILED) || booking.getStatus().equals(Booking.BookingStatus.CANCELLED));
        if (anyAssociatedBookingFailed) {
            tripBooking.setStatus(Booking.BookingStatus.CANCELLED);
        } else {
            tripBooking.setStatus(Booking.BookingStatus.CONFIRMED);
        }
    }

    /**
     * Update the associate booking details in trip booking details
     *
     * @param target  service web target
     * @param booking Associated booking details
     */
    private static void mergeAssociateBookingDetails(WebTarget target, Booking booking) {
        Response response = target.path(booking.getEncodedId()).request().get(); // associated service must be listening on this path /bookingId
        booking.merge(response.readEntity(Booking.class));
    }

}
