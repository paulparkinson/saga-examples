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
package com.oracle.trm.lra.demo.model;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public class Booking {
    private String id;
    private String name;
    private BookingStatus status;
    private String type;
    private Booking[] details;
    private String encodedId;

    private static final Logger log = Logger.getLogger(Booking.class.getSimpleName());

    public Booking() {
    }

    public Booking(String id, String name, String type, Booking... bookings) {
        this(id, name, type, BookingStatus.PROVISIONAL, bookings);
    }

    public Booking(String id, String name, String type, BookingStatus status, Booking[] details) {
        init(id, name, type, status, details);
    }

    public Booking(Booking booking) {
        this.init(booking.getId(), booking.getName(), booking.getType(), booking.getStatus(), null);
        details = new Booking[booking.getDetails().length];
        IntStream.range(0, details.length).forEach(i -> details[i] = new Booking(booking.getDetails()[i]));
    }

    private void init(String id, String name, String type, BookingStatus status, Booking[] details) {
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

    public Booking[] getDetails() {
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

    public void setDetails(Booking[] details) {
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

    public boolean merge(Booking booking) {
        if (!id.equals(booking.getId())) {
            return false; // or throw an exception
        }
        this.name = booking.getName();
        this.status = booking.getStatus();
        if(booking.getDetails() != null) {
            for (Booking childBooking : booking.getDetails()) {
                Booking curBooking = Arrays.stream(this.details).filter(a -> a.id.equals(childBooking.id)).findFirst().orElse(null);
                if (curBooking != null) curBooking.merge(childBooking);
            }
        }
        return true;
    }

    public enum BookingStatus {
        CONFIRMED, CANCELLED, PROVISIONAL, CONFIRMING, CANCEL_REQUESTED, FAILED;
    }
}
