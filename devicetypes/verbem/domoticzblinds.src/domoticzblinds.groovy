/**
 *  domoticzBlinds
 *
 *  Copyright 2015 Martin Verbeek
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
preferences {
	input("Somfy", "boolean", title: "Somfy stop supported?", description: "Is the Somfy STOP defined?", defaultValue : false)
}   
metadata {
	definition (name: "domoticzBlinds", namespace: "verbem", author: "Martin Verbeek") {
    
        capability "Actuator"
        capability "Switch"
        capability "Switch Level"
        capability "Refresh"
        capability "Polling"

        // custom attributes
        attribute "networkId", "string"


        // custom commands
        command "parse"     // (String "<attribute>:<value>[,<attribute>:<value>]")
        command "close"
        command "open"
        command "stop"
        command "setLevel"

    }

    tiles (scale: 2) {
	    multiAttributeTile(name:"richDomoticzBlind", type:"generic",  width:6, height:4, canChangeIcon: true) {
        	tileAttribute("device.status", key: "PRIMARY_CONTROL") {
                attributeState "default", label:'${currentValue}', inactiveLabel:false
                attributeState "Open", label:" Open ", backgroundColor:"#19f028"
                attributeState "Off", label:" Open ", backgroundColor:"#19f028"	
                attributeState "Opening", label:"Opening", backgroundColor:"#FE9A2E"
                attributeState "Stopped", label:"Stopped", backgroundColor:"#11A81C"
                attributeState "Closed", label:"Closed",  backgroundColor:"#08540E"
                attributeState "On", label:"Closed",  backgroundColor:"#08540E"
                attributeState "Closing", label:"Closing",  backgroundColor:"#FE9A2E"
            }
        }
 
        
        standardTile("open", "device.switch", inactiveLabel:false, decoration:"flat") {
            state "default", label:'Open', icon:"st.doors.garage.garage-opening",
                action:"open"
        }

        standardTile("stop", "device.switch", inactiveLabel:false, decoration:"flat") {
            state "default", label:'Stopped/My', icon:"st.doors.garage.garage-open",
                action:"stop"
        }

        standardTile("close", "device.switch", inactiveLabel:false, decoration:"flat") {
            state "default", label:'Close', icon:"st.doors.garage.garage-closing",
                action:"close"
        }

        standardTile("debug", "device.motion", inactiveLabel: false, decoration: "flat") {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main(["richDomoticzBlind"])
        details(["richDomoticzBlind", "open", "stop", "close", "debug"])

    }    
}

// parse events into attributes
def parse(String message) {
    log.debug "parse(${message})"

    Map msg = stringToMap(message)
    if (msg?.size() == 0) {
        log.error "Parse- Invalid message: ${message}"
        return null
    }


    if (msg.containsKey("switch")) {
    	log.debug "Parse- value  ${msg.switch}"
        switch (msg.switch.toUpperCase()) {
        case "ON": close(); break
        case "STOP": stop(); break
        case "OFF": open(); break
        sendPush( "Parse- Invalid message: ${message}")
        return null
        }
    }
//    STATE()
    return null
}

// handle commands

def on() {
close()
}

def off() {
open()
}

def close() {
    if (parent) {
        sendEvent(name:'status', value:"Closing" as String)
        parent.domoticz_on(getIDXAddress())
    }
}

def refresh() {
    if (parent) {
        parent.domoticz_poll(getIDXAddress())
    }
}

def poll() {
    if (parent) {
        parent.domoticz_poll(getIDXAddress())
    }
}

def open() {
    if (parent) {
        sendEvent(name:'status', value:"Opening" as String)
        parent.domoticz_off(getIDXAddress())
    }
}

def stop() {
    if (parent) {
   		sendEvent(name:'status', value:"Stopped" as String)
        parent.domoticz_stop(getIDXAddress())
    }
}
/* special implementation through setlevel for STOP somfy command if device setting.Somfy = true */
def setLevel() {
    if (parent && settings.Somfy) {
   		sendEvent(name:'status', value:"Stopped" as String)
        parent.domoticz_stop(getIDXAddress())
    }
}

private String makeNetworkId(ipaddr, port) {

    String hexIp = ipaddr.tokenize('.').collect {
        String.format('%02X', it.toInteger())
    }.join()

    String hexPort = String.format('%04X', port)
    return "${hexIp}:${hexPort}"
}

// gets the address of the device
private getHostAddress() {
	
    def ip = getDataValue("ip")
    def port = getDataValue("port")
    
    if (!ip || !port) {
        def parts = device.deviceNetworkId.split(":")
        if (parts.length == 3) {
            ip = parts[0]
            port = parts[1]
        } else {
            log.warn "Can't figure out ip and port for device: ${device.id}"
        }
    }

    //log.debug "Using ip: $ip and port: $port for device: ${device.id}"
    return ip + ":" + port

}

// gets the IDX address of the device
private getIDXAddress() {
	
    def idx = getDataValue("idx")
        
    if (!idx) {
        def parts = device.deviceNetworkId.split(":")
        if (parts.length == 3) {
            idx = parts[2]
		}else if(parts.length == 2 && parts[0].endsWith('-IDX')){
			idx = parts[1]
        } else {
            log.warn "Can't figure out idx for device: ${device.id}"
        }
    }

    //log.debug "Using IDX: $idx for device: ${device.id}"
    return idx
}

private Integer convertHexToInt(hex) {
    return Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

// gets the address of the hub
private getCallBackAddress() {
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}

/*----------------------------------------------------*/
/*			execute event can be called from the service manager!!!
/*----------------------------------------------------*/
def generateEvent (Map results) {
results.each { name, value ->
	log.info name + " " + value
	sendEvent(name:"${name}", value:"${value}")
    }
    return null
}