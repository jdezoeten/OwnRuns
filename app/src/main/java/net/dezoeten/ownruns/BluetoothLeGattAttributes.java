/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dezoeten.ownruns;

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class BluetoothLeGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();

    // Generic Access
    public static String GAP_DEVICE_NAME = "00002a00-0000-1000-8000-00805f9b34fb";
    public static String GAP_APPEARANCE = "00002a01-0000-1000-8000-00805f9b34fb";


    // Battery Service

    // Heart Rate
    public static String HEART_RATE_SERVICE             = "0000180d-0000-1000-8000-00805f9b34fb";
    public static String HEART_RATE_MEASUREMENT         = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG   = "00002902-0000-1000-8000-00805f9b34fb";

    // Running Speed and Cadence
    public static String RSC_SERVICE                = "00001814-0000-1000-8000-00805f9b34fb";
    public static String RSC_MEASUREMENT            = "00002a53-0000-1000-8000-00805f9b34fb";
    public static String RSC_FEATURE                = "00002a54-0000-1000-8000-00805f9b34fb";
    public static String SENSOR_LOCATION            = "00002a5d-0000-1000-8000-00805f9b34fb";
    public static String SC_CONTROL_POINT           = "00002a55-0000-1000-8000-00805f9b34fb";

    // Cycling Power
    public static String CYCLING_POWER_SERVICE      = "00001818-0000-1000-8000-00805f9b34fb";
    public static String CYCLING_POWER_FEATURE      = "00002A65-0000-1000-8000-00805f9b34fb";
    public static String CYCLING_POWER_MEASUREMENT  = "00002A63-0000-1000-8000-00805f9b34fb";

    // FitSHOW Custom Service
    public static String FITSHOW_CUSTOM_SERVICE     = "0000fff0-0000-1000-8000-00805f9b34fb";
    public static String FITSHOW_COMMAND            = "0000fff2-0000-1000-8000-00805f9b34fb";
    public static String FITSHOW_RESPONSE           = "0000fff1-0000-1000-8000-00805f9b34fb";

    static {
        // Sample Services.
        attributes.put("00001800-0000-1000-8000-00805f9b34fb", "Generic Access");

        attributes.put(HEART_RATE_SERVICE, "Heart Rate Service");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");

        attributes.put("00001801-0000-1000-8000-00805f9b34fb", "Generic Attribute Service");
        attributes.put("00001801-0000-1000-8000-00805f9b34fb", "Battery Service");
        attributes.put("00001826-0000-1000-8000-00805f9b34fb", "Fitness Machine Service");

        // Generic Access Protocol
        attributes.put(GAP_DEVICE_NAME, "Device Name");
        attributes.put(GAP_APPEARANCE, "Appearance");

        // Battery Service


        // Heart Rate Service Characteristics.
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");

        // Running Speed and Cadence Characteristics
        attributes.put(RSC_SERVICE, "Running Speed and Cadence");
        attributes.put(RSC_MEASUREMENT, "RSC Measurement");
        attributes.put(RSC_FEATURE, "RSC Feature");
        attributes.put(SENSOR_LOCATION, "Sensor Location");
        attributes.put(SC_CONTROL_POINT, "SC Control Point");

        // Cycling Power
        attributes.put(CYCLING_POWER_SERVICE, "Cycling Power");
        attributes.put(CYCLING_POWER_FEATURE, "Cycling Power Feature");
        attributes.put(CYCLING_POWER_MEASUREMENT, "Cycling Power Measurement");

        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
