/**
 *  kvChild 0.1.5b
 	
    0.1.5b	changed disable function to immediate when zone is running
 	0.1.5a	patch null error on end report creation, before there is an end report...	
 	0.1.5	pulled pressure polling, as it has proven worthless
    		added quick recovery
    0.1.4	fixed null race condition when adding new zone.        
 	0.1.3	fixed bug where zoneSetpoint wouldn't stick when applied while the zone was running
    		reversed order of zone temp setbacks
            updated zone vent close options functionality
    0.1.2a	update for ST's change of default map value handling
    0.1.2	fixed init on settings changes
    0.1.1	fixed bug in zone temp
    0.1.0	added adjustable logging
    		added pressure polling switch option
    0.0.8a	bug fixes, poller and notify
 	0.0.8	actually got zone update support working
 			fixed bug, zone not restarting
    		added app version info on the bottom of the parent page
            added dynamic max opening selection
    0.0.6a	added interim debugging
    0.0.6	added options on what to do when the zone is disabled...
    0.0.5	added disable switch option
    0.0.4	basic reporting
    0.0.3 	added dynamic zone change support while system is running
    		added support for main set point updates while system is running
    0.0.2	added F/C unit detection and display
    
 *  Copyright 2015 Mike Maxwell
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
 
definition(
    name: "keenectZone",
    namespace: "MikeMaxwell",
    author: "Mike Maxwell",
    description: "child application for 'Keenect', do not install directly.",
    category: "My Apps",
    parent: "MikeMaxwell:Keenect",
    iconUrl: "https://raw.githubusercontent.com/MikeMaxwell/smartthings/master/keen-app-icon.png",
    iconX2Url: "https://raw.githubusercontent.com/MikeMaxwell/smartthings/master/keen-app-icon.png",

)

preferences {
	page(name: "main")
    page(name: "advanced")
}

def installed() {
	log.debug "Installed with settings: ${settings}"
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	initialize()
   
}

def initialize() {

	state.vChild = "0.1.5b"
    parent.updateVer(state.vChild)
    subscribe(tempSensors, "temperature", tempHandler)
    //subscribe(vents, "pressure", getAdjustedPressure)
    subscribe(vents, "level", levelHandler)
    subscribe(zoneControlSwitch,"switch",zoneDisableHandeler)
    
    zoneEvaluate(parent.notifyZone())
}

//dynamic page methods
def main(){
	def installed = app.installationState == "COMPLETE"
    def vcNotMet = ventCountNotMet()
	return dynamicPage(
    	name		: "main"
        ,title		: "Zone Configuration"
        ,install	: true
        ,uninstall	: installed
        ){
		     section("Devices"){
             	label(
                   	title		: "Name the zone"
                    ,required	: true
                )
                /*
				only stock device types work in the list below???
                ticket submitted, as this should work, and seems to work for everyone except me...
				*/
                input(
                    name			: "vents"
                    ,title			: "Keen vents in this Zone:"
                    ,multiple		: true
                    ,required		: true
                    //,type			: "device.KeenHomeSmartVent"
                    ,type			: "capability.switchLevel"
                    ,submitOnChange	: true
				)
				/*
				if (vents){
  					//spin through sizing selections
                    vents.each{ vent ->
                        input(
            				name			: vent.id
                			,title			: vent.displayName + " size:"
                			,multiple		: false
                			,required		: true
                			,type			: "enum"
                    		,submitOnChange	: false
                            ,options		: [["40":"4x10"],["48":"4x12"],["60":"6x10"],["72":"6x12"]]
            			) 
                    }
                }
                */
 				input(
            		name		: "tempSensors"
                	,title		: "Temp Sensors:"
                	,multiple	: false
                	,required	: true
                	,type		: "capability.temperatureMeasurement"
                    ,submitOnChange	: false
            	) 
                /* out for now...
				input(
            		name		: "motionSensors"
                	,title		: "Motion Sensors:"
                	,multiple	: true
                	,required	: false
                	,type		: "capability.motionSensor"
            	)   
                */
            }
            section("Settings"){
				input(
            		name			: "minVo"
                	,title			: "Minimum vent opening"
                	,multiple		: false
                	,required		: true
                	,type			: "enum"
                    ,options		: minVoptions()
                    ,submitOnChange	: true
            	) 
                if (minVo){
				input(
            		name			: "maxVo"
                	,title			: "Maximum vent opening"
                	,multiple		: false
                	,required		: true
                	,type			: "enum"
                    ,options		: maxVoptions()
                    ,defaultValue	: "100"
                    ,submitOnChange	: true
            	) 
                }
				input(
            		name			: "heatOffset"
                	,title			: "Heating offset, (above or below main thermostat)"
                	,multiple		: false
                	,required		: true
                	,type			: "enum"
                    ,options 		: zoneTempOptions()
                    ,defaultValue	: "0"
                    ,submitOnChange	: false
            	) 
				input(
            		name			: "coolOffset"
                	,title			: "Cooling offset, (above or below main thermostat)"
                	,multiple		: false
                	,required		: true
                	,type			: "enum"
                    ,options 		: zoneTempOptions()
                    ,defaultValue	: "0"
                    ,submitOnChange	: false
            	)
            }
            section("Options"){
                def froTitle = 'Close vents at cycle end is '
                if (!ventCloseWait || ventCloseWait == "-1"){
                	froTitle = froTitle + "[off]"
                } else {
                	froTitle = froTitle + "[on]"
                }
            	input(
            		name			: "ventCloseWait"
                    ,title			: froTitle
                	,multiple		: false
                	,required		: true
                	,type			: "enum"
                	,options		: [["-1":"Do not close"],["0":"Immediate"],["60":"After 1 Minute"],["120":"After 2 Minutes"],["300":"After 5 Minutes"]]
                	,submitOnChange	: true
                   	,defaultValue	: "-1"
            	)
                
            	def zcsTitle = zoneControlSwitch ? "Optional zone disable switch: when on, zone is enabled, when off, zone is disabled " : "Optional zone disable switch"
                input(
            		name			: "zoneControlSwitch"
                	,title			: zcsTitle 
                	,multiple		: false
                	,required		: false
                	,type			: "capability.switch"
                    ,submitOnChange	: true
            	)  
				
                //advanced hrefs...
                //def afTitle = "Advanced features: " + (vPolling ?  "Vent polling is [on]" : "Vent polling is [off]") + ', Log Level is ' + getLogLevel(settings.logLevel)
                def afTitle = "Advanced features:"
				def afDesc = '\n\tLog level is ' + getLogLevel(settings.logLevel) + '\n\t' + (quickRecovery ?  "Quick recovery is [on]" : "Quick recovery is [off]") + '\n\t' + (sendEventsToNotifications ?  "Notification feed is [on]" : "Notification feed is [off]")
                href( "advanced"
                    ,title			: afTitle
					,description	: afDesc
					,state			: null
				)
            }
	}
}

def advanced(){
    return dynamicPage(
    	name		: "advanced"
        ,title		: "Advanced Options"
        ,install	: false
        ,uninstall	: false
        ){
         section(){
         		def qrTitle = quickRecovery ?  "Quick recovery is [on]" : "Quick recovery is [off]" 
          		input(
            		name			: "quickRecovery"
                	,title			: qrTitle 
                	,multiple		: false
                	,required		: false
                	,type			: "bool"
                    ,submitOnChange	: true
                    ,defaultValue	: false
            	)          
         		/*
         		def vpTitle = vPolling ?  "Vent polling is [on]" : "Vent polling is [off]" 
          		input(
            		name			: "vPolling"
                	,title			: vpTitle 
                	,multiple		: false
                	,required		: false
                	,type			: "bool"
                    ,submitOnChange	: true
                    ,defaultValue	: false
            	) 
                */
         		input(
            		name			: "logLevel"
                	,title			: "IDE logging level" 
                	,multiple		: false
                	,required		: true
                	,type			: "enum"
                    ,options		: getLogLevels()
                	,submitOnChange	: false
                   	,defaultValue	: "10"
            	)            
         		def etnTitle = sendEventsToNotifications ?  "Send lite events to notification feed is [on]" : "Send lite events to notification feed is [off]" 
          		input(
            		name			: "sendEventsToNotifications"
                	,title			: etnTitle 
                	,multiple		: false
                	,required		: false
                	,type			: "bool"
                    ,submitOnChange	: true
                    ,defaultValue	: false
            	)                 
        }
    }
}

//zone control methods
def zoneEvaluate(params){
	logger(30,"debug","zoneEvaluate:enter-- parameters: ${params}")
    //log.warn "params:${params}"
	//param data structures
    /*
    [msg:stat, data:[mainState:heat, mainStateChange:false, mainMode:heat, mainModeChange:false, mainCSP:80.0, mainCSPChange:false, mainHSP:74.0, mainHSPChange:true, mainOn:true]]
    [stat:[mainMode:heat|cool|auto,mainState:heat|cool|idle,mainCSP:,mainHSP:,mainOn:true|false]]
    [msg:temp, data:[value:]]
    [msg:vent, data:[value:]]
    [msg:"zoneSwitch", data:[zoneIsEnabled:true|false]]
    [msg:pressure, data:[value:,isOK:true|false]]
    [msg:self, data:[evt:"updated"]]
    */
	
    // variables
    def evaluateVents = false
    def msg = params.msg
    def data = params.data
    //main states
    def mainStateLocal = state.mainState ?: ""
    def mainModeLocal = state.mainMode ?: ""
	def mainHSPLocal = state.mainHSP  ?: 0
	def mainCSPLocal = state.mainCSP  ?: 0
	def mainOnLocal = state.mainOn  ?: ""
    def mainQuickLocal =  state.mainQuick ?: false
    if (mainStateLocal == null || mainModeLocal == null || mainHSPLocal == null || mainCSPLocal == null || mainOnLocal == null || mainQuickLocal == null){
    	log.warn "one or more main state variables are null, mainState:${mainStateLocal} mainMode:${mainModeLocal} mainHSP:${mainHSPLocal} mainCSP:${mainCSPLocal} mainOn:${mainOnLocal} mainQuickLocal:${mainQuickLocal}"
    }
 	//zone states    
    //def zoneDisablePendingLocal = state.zoneDisablePending ?: false
	def zoneDisabledLocal = state.zoneDisabled ?: false
    def runningLocal
    
    //always fetch these since the zone ownes them
    def zoneTempLocal = tempSensors.currentValue("temperature").toFloat()
    def coolOffsetLocal = settings.coolOffset.toInteger()
    def heatOffsetLocal = settings.heatOffset.toInteger()
    def maxVoLocal = settings.maxVo.toInteger()
    def minVoLocal = settings.minVo.toInteger() 
    
    

   	if (coolOffsetLocal == null || heatOffsetLocal == null ){ //|| maxVoLocal == null || minVoLocal == null){
    	log.warn "one or more local inputs are null, coolOffset:${coolOffsetLocal} heatOffset:${heatOffsetLocal} " //maxVo:${maxVoLocal} minVo:${minVoLocal}"
    }    
    def zoneCSPLocal = mainCSPLocal + coolOffsetLocal
    def zoneHSPLocal = mainHSPLocal + heatOffsetLocal

    
    switch (msg){
    	case "stat" :
        		/*
                [msg:"stat",
                data:[
                	initRequest:false,
                    mainState:mainState,
                    mainStateChange:mainStateChange,
                    mainMode:mainMode,
                    mainModeChange:mainModeChange,
                    mainCSP:mainCSP,
                    mainCSPChange:mainCSPChange,
                    mainHSP:mainHSP,
                    mainHSPChange:mainHSPChange,
                    mainOn:mainOn,
                    delay:delay
                    mainQuick: true|false
                 ]]
        		*/
                //initial request for info during app install and zone update
                if (data.initRequest){
                    evaluateVents = data.mainOn
                    //log.info "zoneEvaluate- init zone request, evaluateVents: ${evaluateVents}"
                //set point changes, ignore setbacks
                } else if (data.mainOn && (mainHSPLocal < data.mainHSP || mainCSPLocal > data.mainCSP)) {
                    evaluateVents = true
                    logger(30,"info","zoneEvaluate- set point changes, evaluate: ${true}")
                //system state changed
                } else if (data.mainStateChange){
                	//system start up
                	if (data.mainOn && !zoneDisabledLocal){
                    	//fire up temp/pressure snapshot here
                        evaluateVents = true
                        //if (vPolling){
                        //	getInitialPressure()
                        //	runIn(60,pollVents)
                        //}
                        
                        //logger(30,"info","zoneEvaluate- system start up, evaluate: ${evaluateVents}, vent polling started: ${vPolling}")
                        logger(30,"info","zoneEvaluate- system start up, evaluate: ${evaluateVents}")
                        logger(10,"info","Main HVAC is on and ${data.mainState}ing")
                        
                    //system shut down
                    } else if (!data.mainOn){
                    	//zoneDisablePendingLocal = false
                    	runningLocal = false
                        def asp
                        def d
                        if (zoneTempLocal && asp){
                        	asp = state.activeSetPoint
                            d = (zoneTempLocal - asp).toFloat()
                            d = d.round(1)
                        }
                       	//def asp = state.activeSetPoint
                        //def d = (zoneTempLocal - asp).toFloat()
                        //d = d.round(1)
                        state.endReport = "\n\tsetpoint: ${tempStr(asp)}\n\tend temp: ${tempStr(zoneTempLocal)}\n\tvariance: ${tempStr(d)}\n\tvent levels: ${vents.currentValue("level")}%"        
                    	logger(10,"info","Main HVAC has shut down.")                        
                        
						//check zone vent close options from zone and from parent
                        def delaySeconds = 0
                        def zoneCloseOption = settings.ventCloseWait.toInteger()
                        if (zoneCloseOption != -1){
                        	delaySeconds = zoneCloseOption
                        	if (data.delay != -1){
                            	delaySeconds = delaySeconds + data.delay
                            } 
       						if (delaySeconds == 0){
                				logger(10,"warn", "Vents closed via close vents option")
        						setVents(0)
        					} else {
                				logger(10,"warn", "Vent closing is scheduled in ${delaySeconds} seconds")
        						runIn(delaySeconds,delayClose)
        					}                            
                        } 
     				}
                } else if (data.mainQuickChange && settings.quickRecovery){
                	if (data.mainQuick) logger(10,"info","Main HVAC entered quick recovery mode.")
                    else logger(10,"info","Main HVAC exited quick recovery mode.")
                    evaluateVents = true
   				}
 				//log.warn "data.mainQuickChange:${data.mainQuickChange} data.mainQuick:${data.mainQuick} settings.quickRecovery:${settings.quickRecovery}"
                
                //always update data
                mainStateLocal = data.mainState
                mainModeLocal = data.mainMode
                mainHSPLocal = data.mainHSP
                mainCSPLocal = data.mainCSP
                mainOnLocal = data.mainOn
                mainQuickLocal =  data.mainQuick 
                zoneCSPLocal = mainCSPLocal + coolOffsetLocal
                zoneHSPLocal = mainHSPLocal + heatOffsetLocal
        	break
        case "temp" :
        		//data:["tempChange"]
        		//logger(30,"debug","zoneEvaluate- msg: ${msg}, data: ${data}")
                //process changes if zone is not disabled
                //if (!zoneDisabledLocal || zoneDisablePendingLocal){
                if (!zoneDisabledLocal){
                	logger(30,"debug","zoneEvaluate- zone temperature changed, zoneTemp: ${zoneTempLocal}")
                	evaluateVents = true
                } else {
                	logger(30,"warn","zoneEvaluate- ${msg}, no matching events")
                }
        	break
        case "vent" :
        		logger(30,"debug","zoneEvaluate- msg: ${msg}, data: ${data}")
                
        	break
        case "zoneSwitch" :
        		//[msg:"zoneSwitch", data:[zoneIsEnabled:true|false]]
                //fire up zone since it was activated
                if (data.zoneIsEnabled){
                	evaluateVents = true
                } else {
                	runningLocal = false
                }
                /*
                if (mainOnLocal && data.zoneIsEnabled){
                	logger(30,"debug","zoneEvaluate- zone was enabled, data: ${data}")
                    //logger(10,"warn", "Vents closed via close vents option")
                	evaluateVents = true
                //zone is active, zone switch went inactive
                } else if (mainOnLocal && !data.zoneIsEnabled) {
                	zoneDisabledLocal = true
                    //zoneDisablePendingLocal = true
                    runningLocal = false
                } else {
                	logger(30,"warn","zoneEvaluate- ${msg}, no matching events, data: ${data}")
                }
                */
        	break
        case "pressure" :
        		logger(30,"debug","zoneEvaluate- msg: ${msg}, data: ${data}")
                
        	break
        //no longer used???...
        case "self" :
        		//[msg:self, data:[settingsChanged:true|false]
        		//logger(30,"debug","zoneEvaluate- msg: ${msg}, data: ${data}")
                if (data.settingsChanged){
                	//[msg:"self", data:[maxVo:maxVo.toInteger(),minVo:minVo.toInteger(),coolOffset:coolOffset.toInteger(),heatOffset:heatOffset.toInteger()]]
                	logger(30,"debug","zoneEvaluate- zone settingsChanged, data: ${data}")
                	evaluateVents = true
                }
        	break
            
    }    
    
    //always check for main quick
   	if (settings.quickRecovery && mainQuickLocal) {
		maxVoLocal = 100
    }
    //write state
    state.mainState = mainStateLocal
    state.mainMode = mainModeLocal
	state.mainHSP = mainHSPLocal
	state.mainCSP = mainCSPLocal
	state.mainOn = mainOnLocal
    state.zoneCSP = zoneCSPLocal
    state.zoneHSP = zoneHSPLocal
    state.zoneTemp = zoneTempLocal
    state.mainQuick = mainQuickLocal
    state.zoneDisablePending = zoneDisablePendingLocal
	state.zoneDisabled = zoneDisabledLocal
  
    if (evaluateVents){
    	def slResult = ""
       	if (mainStateLocal == "heat"){
        	state.activeSetPoint = zoneHSPLocal
       		if (zoneTempLocal >= zoneHSPLocal){
           		slResult = setVents(minVoLocal)
             	logger(10,"info", "Zone temp is ${tempStr(zoneTempLocal)}, heating setpoint of ${tempStr(zoneHSPLocal)} is met${slResult}")
				runningLocal = false
          	} else {
				slResult = setVents(maxVoLocal)
				logger(10,"info", "Zone temp is ${tempStr(zoneTempLocal)}, heating setpoint of ${tempStr(zoneHSPLocal)} is not met${slResult}")
				runningLocal = true
           	}            	
        } else if (mainStateLocal == "cool"){
        	state.activeSetPoint = zoneCSPLocal
       		if (zoneTempLocal <= zoneCSPLocal){
				slResult = setVents(minVoLocal)
                logger(10,"info", "Zone temp is ${tempStr(zoneTempLocal)}, cooling setpoint of ${tempStr(zoneCSPLocal)} is met${slResult}")
                runningLocal = false
       		} else {
				slResult = setVents(maxVoLocal)
                logger(10,"info", "Zone temp is ${tempStr(zoneTempLocal)}, cooling setpoint of ${tempStr(zoneCSPLocal)} is not met${slResult}")
                runningLocal = true
           	}                        
      	} else {
            logger(10,"error","zoneEvaluate- evaluateVents, mainState: ${mainStateLocal}, zoneTemp: ${zoneTempLocal}, zoneHSP: ${zoneHSPLocal}, zoneCSP: ${zoneCSPLocal}")
       	}
    }
    //write state
 	state.running = runningLocal
    

    logger(40,"debug","zoneEvaluate:exit- ")
}

//event handlers
def levelHandler(evt){
	logger(40,"debug","levelHandler:enter- ")
	logger(30,"debug","levelHandler- evt name: ${evt.name}, value: ${evt.value}, rdLen: ${evt.description == ""}")
    
    def ventData = state."${evt.deviceId}"
    def v = evt.value.toFloat().round(0).toInteger()
    def t = evt.date.getTime()
    if (ventData != null){
        //request
        if (evt.description == ""){
			ventData.voRequest = v	
            ventData.voRequestTS = t	
		//response
		} else {
        	ventData.voResponse = v
            ventData.voResponseTS = t
            ventData.voTTC = ((t - ventData.voRequestTS) / 1000).toFloat().round(1)
        }
        state."${evt.deviceId}" = ventData
    } else {
    	//request
    	if (evt.description == ""){
    		state."${evt.deviceId}" =  [voRequest:"${v}",voRequestTS:t,voResponse:null,voResponseTS:null,voTTC:null] 
        //response
        } else {
        	state."${evt.deviceId}" =  [voRequest:null,voRequestTS:t,voResponse:null,voResponseTS:null,voTTC:null] 
        }
    }
    
    logger(40,"debug","levelHandler:exit- ")
}

def zoneDisableHandeler(evt){
    logger(30,"debug","zoneDisableHandeler- evt name: ${evt.name}, value: ${evt.value}")
    if (evt.isStateChange()){
    	def zoneIsEnabled = evt.value == "on"
        if (zoneIsEnabled){
        	logger(10,"warn", "Zone was enabled via: [${zoneControlSwitch.displayName}]")
        } else {
        	logger(10,"warn", "Zone was disabled via: [${zoneControlSwitch.displayName}]")
        }
        zoneEvaluate([msg:"zoneSwitch", data:[zoneIsEnabled:zoneIsEnabled]])
    }
}

def tempHandler(evt){
    logger(40,"debug","tempHandler- evt name: ${evt.name}, value: ${evt.value}")
    state.zoneTemp = evt.value.toFloat()
    if (state.mainOn){
    	logger(30,"debug","tempHandler- tempChange, value: ${evt.value}")
    	zoneEvaluate([msg:"temp", data:["tempChange"]])	
    }
    
}

def getAdjustedPressure(evt){
	logger(40,"debug","getAdjustedPressure:enter- ")
	if (state.mainOn || (vPolling == true)){
    	logger(30,"info","getAdjustedPressure- evt name: ${evt.name}, value: ${evt.value}")
    	def vid = evt.deviceId
        def vent = vents.find{it.id == vid}
        
     	def stdT = 273.15 //standard temperature, kelvin
    	def stdP = 101325.0 //standard pressure, pascal
        def stdD = 1.2041 //standard air density, kg/m3
        def vo = vent.currentValue("level").toFloat().round(0).toInteger()
   		def P1 = vent.currentValue("pressure").toFloat()
        def T = vent.currentValue("temperature").toFloat()
		def T1 = tempToK(T)
       	def pAdjusted = ((P1 * stdT)/T1) //pascal
        def pVelocity = Math.sqrt((2 * P1)/stdD).round(0).toInteger()
        def pVelocityAdjusted = Math.sqrt((2 * pAdjusted)/stdD).round(0).toInteger()
        
        def roAdj
        def roAct
        def vAdj
        def vAct
        if (state."${vid}"){
        	roAdj = (state."${vid}".pInitAdj ?: pAdjusted) - pAdjusted
            roAct = (state."${vid}".pInitAct ?: P1) - P1
            vAdj = (state."${vid}".vInitAdj ?: pVelocityAdjusted) - pVelocityAdjusted
            vAct = (state."${vid}".vInitAct ?: pVelocity) - pVelocity
        }
        logger(15,"debug","getAdjustedPressure- [${vent.displayName}] monitor~ roAdj: ${roAdj.round(1)}, roAct: ${roAct.round(1)}, vAdj: ${vAdj}, vAct: ${vAct}, adjusted~ p: ${pAdjusted.round(0).toInteger()} Pa, v: ${pVelocityAdjusted}, actuals~ p: ${P1.round(0).toInteger()} Pa, v: ${pVelocity}, vo: ${vo}%, mainOn: ${state.mainOn}")
        //logger(15,"debug","getAdjustedPressure- [${vent.displayName}] monitor~ roAdj: ${roAdj.round(1)}, roAct: ${roAct.round(1)}  adjusted~ pressure: ${pAdjusted.round(0).toInteger()} Pa, pressure: ${P1.round(0).toInteger()} Pa, vo: ${vo}%, mainOn: ${state.mainOn}")
    }
    logger(40,"debug","getAdjustedPressure:exit- ")
}

def getInitialPressure(){
	//logger(15,"info","Getting startup pressures...")
    def stdT = 273.15 //standard temperature, kelvin
   	def stdP = 101325.0 //standard pressure, pascal
   	def stdD = 1.2041 //standard air density, kg/m3

	vents.each{ vent ->
    	def vo = vent.currentValue("level").toFloat().round(0).toInteger()
   		def P1 = vent.currentValue("pressure").toFloat()
        def T = vent.currentValue("temperature").toFloat()
		def T1 = tempToK(T)
       	def pAdjusted = ((P1 * stdT)/T1) //pascal
        def pVelocity = Math.sqrt((2 * P1)/stdD).round(0).toInteger()
        def pVelocityAdjusted = Math.sqrt((2 * pAdjusted)/stdD).round(0).toInteger()
        
        //logger(15,"warn","getInitialPressure- [${vent.displayName}] adjusted~ pressure: ${pAdjusted.round(0).toInteger()} Pa, velocity: ${pVelocityAdjusted} m/s, actuals~ temp: ${T1} K, pressure: ${P1.round(0).toInteger()} Pa, velocity: ${pVelocity} m/s, vo: ${vo}%, mainOn: ${state.mainOn}")
        logger(15,"warn","getInitialPressure- [${vent.displayName}] start~ roAdj: ${pAdjusted.round(0).toInteger()}, roAct: ${P1.round(0).toInteger()}, vAdj: ${pVelocityAdjusted}, vAct: ${pVelocity}, adjusted~ p: ${pAdjusted.round(0).toInteger()} Pa, actuals~ p: ${P1.round(0).toInteger()} Pa, vo: ${vo}%")
        if (state."${vent.id}" != null){
        	state."${vent.id}".pInitAdj = pAdjusted.round(0).toInteger()
            state."${vent.id}".pInitAct = P1.round(0).toInteger()
            state."${vent.id}".vInitAdj = pVelocityAdjusted
            state."${vent.id}".vInitAct = pVelocity
        } else {
        	state."${vent.id}" = [pInitAdj:pAdjusted.round(0).toInteger(),pInitAct:P1.round(0).toInteger(),vInitAdj:pVelocityAdjusted,vInitAct:pVelocity,voRequest:"",voActual:""]
        }
    }
}

//misc utility methods
def logger(displayLevel,errorLevel,text){
	//input logLevel 1,2,3,4,-1
    /*
    [1:"Lite"],[2:"Moderate"],[3:"Detailed"],[4:"Super nerdy"]
    input 	logLevel
    
    1		Lite		
    2		Moderate	
    3		Detailed
    4		Super nerdy
    
    errorLevel 	color		number
    error		red			5
    warn		yellow		4
    info		lt blue		3
    debug		dk blue		2
    trace		gray		1
    */
    def logL = 10
    if (logLevel) logL = logLevel.toInteger()
    
    if (logL == 0) {return}//bail
    //else if (logL == 15 && displayLevel == 15) log."${errorLevel}"(text)
    else if (logL >= displayLevel){
    	log."${errorLevel}"(text)
        if (sendEventsToNotifications && displayLevel == 10){
        	def nixt = now() + location.timeZone.rawOffset
        	def today = new Date(nixt).format("HH:mm:ss.Ms")
        	text = today + ": " + text
        	sendNotificationEvent(app.label + ": " + text) //sendEvent(name: app.label , value: text, descriptionText: text, isStateChange : true)
        }
    }
 }

def setVents(newVo){
	logger(40,"debug","setVents:enter- ")
	logger(30,"warn","setVents- newVo: ${newVo}")
	//state.voRequest = newVo
    def result = ""
    def changeRequired = false
    
	vents.each{ vent ->
    	def changeMe = false
        def previousRequest
        def previousActual
		def crntVo = vent.currentValue("level").toInteger()
        /*
        	0 = 0 for sure
        	> 90 = 100, usually
        	the remainder is a crap shoot
            0 == switch == "off"
            > 0 == switch == "on"
            establish an arbitrary +/- threshold
            if currentLevel is +/- 5 of requested level, call it good
            otherwise reset it
		*/
        //new code
        if (newVo != crntVo){
        	def lB = crntVo - 5
            def uB = crntVo + 5
        	if (newVo == 100 && crntVo < 90){
            	//logger(10,"info","newVo == 100 && crntVo < 90: ${newVo == 100 && crntVo < 90}")
            	changeMe = true
            } else if ((newVo < lB || newVo > uB) && newVo != 100){
            	//logger(10,"info","newVo < lB || newVo > uB && newVo != 100: ${(newVo < lB || newVo > uB) && newVo != 100}")
            	changeMe = true
            }
        }
        if (changeMe){
        	changeRequired = true
        	vent.setLevel(newVo)
        }
        //logger(10,"info","[${vent.displayName}], new vo: ${newVo}, current vo: ${crntVo}, changeRequired: ${changeMe}")
        logger(30,"info","setVents- [${vent.displayName}], changeRequired: ${changeMe}, new vo: ${newVo}, current vo: ${crntVo}")
    }
    //state.voRequest = newVo
    def mqText = ""
    if (state.mainQuick && settings.quickRecovery && newVo == 100){
    	mqText = ", quick recovery active"
    }
    if (changeRequired) result = ", setting vents to ${newVo}%${mqText}"
    else result = ", vents at ${newVo}%${mqText}"
 	return result
    logger(40,"debug","setVents:exit- ")
}

def pollVents(){
	if (vPolling){
		logger(20,"warn","pollVents- polling vents")
   		vents.getTemperature()
		vents.getPressure()
   		if (state.mainOn == null) return
   		if (state.mainOn) runIn(60,pollVents)
    }
}

def pollVentsII(){
	log.warn "polling ventsII"
    vents.getTemperature()
	vents.getPressure()
    if (vPolling == true) runIn(60,pollVentsII)
}

def delayClose(){
    setVents(0)
    logger(10,"warn","Vent close executed")
}

def tempStr(temp){
    def tc = state.tempScale ?: location.temperatureScale
    if (temp) return "${temp.toString()}°${tc}"
    else return "No data available yet."
}

def tempToK(ct){
   	def K
   	if (state.tempScale == "F"){
		//F to K: [K] = ([°F] + 459.67) × 5⁄9
        K = ((ct + 459.67) * 5) / 9
    } else {
    	//C to K: [K] = [°C] + 273.15
        K = ct + 273.15
    }
	return K.toInteger()        
}

//dynamic page input helpers
//check if these are even used....
def ventCountOptions(){
	def opts = []
    def v410 = (settings.v410 ?: 0).toInteger()
    def v412 = (settings.v412 ?: 0).toInteger()
    def v610 = (settings.v610 ?: 0).toInteger()
    def v612  = (settings.v612 ?: 0).toInteger()
    def remaining = vents.size() + 1 - (v410 + v412 + v610 + v612 )
    
    0.step remaining, 1, {
   		opts.push("${it}")
	}
    //opts.push(["100":"Fully open"])
    return opts
}

def ventCountNotMet(){
	if (!vents) return false
	def v410 = (settings.v410 ?: 0).toInteger()
    def v412 = (settings.v412 ?: 0).toInteger()
    def v610 = (settings.v610 ?: 0).toInteger()
    def v612  = (settings.v612 ?: 0).toInteger()
    def inZone = vents.size()
    return (v410 + v412 + v610 + v612) != inZone
}

def minVoptions(){
	return [["0":"Fully closed"],["5":"5%"],["10":"10%"],["15":"15%"],["20":"20%"],["25":"25%"],["30":"30%"],["35":"35%"],["40":"40%"]]
}

def maxVoptions(){
	def opts = []
    def start = minVo.toInteger() + 5
    start.step 95, 5, {
   		opts.push(["${it}":"${it}%"])
	}
    opts.push(["100":"Fully open"])
    return opts
}

def getLogLevels(){
	//return [["0":"None"],["10":"Lite"],["20":"Moderate"],["30":"Detailed"],["40":"Super nerdy"],["15":"Pressure only"]]
    return [["0":"None"],["10":"Lite"],["20":"Moderate"],["30":"Detailed"],["40":"Super nerdy"]]
}

def getLogLevel(val){
	def logLvl = 'Lite'
    def l = getLogLevels()
    if (val){
    	logLvl = l.find{ it."${val}"}
        logLvl = logLvl."${val}".value
    }
    return '[' + logLvl + ']'
}

def zoneTempOptions(){
	def zo
    if (!state.tempScale) state.tempScale = location.temperatureScale
	if (state.tempScale == "F"){
    	zo = [["5":"5°F"],["4":"4°F"],["3":"3°F"],["2":"2°F"],["1":"1°F"],["0":"0°F"],["-1":"-1°F"],["-2":"-2°F"],["-3":"-3°F"],["-4":"-4°F"],["-5":"-5°F"]]
    } else {
    	zo = [["5":"5°C"],["4":"4°C"],["3":"3°C"],["2":"2°C"],["1":"1°C"],["0":"0°C"],["-1":"-1°C"],["-2":"-2°C"],["-3":"-3°C"],["-4":"-4°C"],["-5":"-5°C"]]
    }
	return zo
}

//legacy data logging and statistics
//spent too much time on this to delete it yet.
def statHandler(evt){
	if (state.runMaps.size() < 10) {
		log.info "event:${evt.value}"
    	def key = evt.date.format("yyyy-MM-dd HH:mm:ss")
    	def v  = evt.value
    	def evtTime = evt.date.getTime()
    	if (v == "heating"){
    		//start
        	state.lastCalibrationStart = key
        	state.startTime = evtTime
        	state.startTemp = tempSensors.currentValue("temperature")
        	log.info "start -time:${state.startTime} -temp:${state.startTemp}"
    	} else if (v == "idle" && state.startTime) {
    		//end
        	state.endTime = evtTime
        	state.endTemp = tempSensors.currentValue("temperature")
        	log.info "end -time:${state.endTime} -temp:${state.endTemp}"
        
        	if (state.endTime > state.startTime && state.endTemp > state.startTemp ){
        		def BigDecimal dTemp  = (state.endTemp - state.startTemp)
            	def BigDecimal dTime = (state.endTime - state.startTime) / 3600000
            	def BigDecimal dph = dTemp / dTime
        		def value = ["dph":"${dph}" ,"dTime":"${dTime}" ,"dTemp":"${dTemp}", "vo":"${vents.currentValue("level")}"]
        		log.info "${value}"
            	if (state.runMaps.size == 0){
            		state.runMaps = ["${key}":"${value}"]
            	} else {
            		state.runMaps << ["${key}":"${value}"]
            	}
            	state.endTime = ""
            	state.startTime = ""
            	state.endTemp = ""
            	state.startTemp = ""
        	}
        }
    }
}

//report methods, called from parent
def getEndReport(){
	return state.endReport ?: "\n\tNo data available yet."
}

def getZoneConfig(){
	//zoneControlSwitch
    def zc = "Not Activated" 
    if (zoneControlSwitch) zc = "is ${zoneControlSwitch.currentValue("switch")} via [${zoneControlSwitch.displayName}]"
	return "\n\tVents: ${vents}\n\ttemp sensors: [${tempSensors}]\n\tminimum vent opening: ${minVo}%\n\tmaximum vent opening: ${maxVo}%\n\theating offset: ${tempStr(heatOffset)}\n\tcooling offset: ${tempStr(coolOffset)}\n\tzone control: ${zc}\n\tversion: ${state.vChild ?: "No data available yet."}"
}

def getZoneState(){
    def s 
    if (state.running == true) s = true
    else s = false
    def report =  "\n\trunning: ${s}\n\tcurrent temp: ${tempStr(state.zoneTemp)}\n\tset point: ${tempStr(state.activeSetPoint)}"
    vents.each{ vent ->
 		def b = vent.currentValue("battery")
        def l = vent.currentValue("level").toInteger()
        
        def d = state."${vent.id}"
        def lrd = "No data yet"
        def rtt = "response time: No data yet"
        if (d){
        	def t = d.voResponseTS
            def r = d.voTTC
            if (t) lrd = (new Date(d.voResponseTS + location.timeZone.rawOffset ).format("yyyy-MM-dd HH:mm")).toString()
            if (r) rtt = "response time: ${r}s"
        }
		
		report = report + "\n\tVent: ${vent.displayName}\n\t\tlevel: ${l}\n\t\tbattery: ${b}%\n\t\t${rtt}\n\t\tlast response: ${lrd}"
    }
    return report
}

def getZoneTemp(){
	return state.zoneTemp
}

def getVentReport(){
	def report = []
    vents.each{ vent ->
    	def P = vent.currentState("pressure")
        def L = vent.currentState("level")
        def T = vent.currentState("temperature")
        def B = vent.currentState("battery")
        def set = [P:[D:P.date.format("yyyy-MM-dd HH:mm:ss") ,V:P.value],L:[D:L.date.format("yyyy-MM-dd HH:mm:ss") ,V:L.value],T:[D:T.date.format("yyyy-MM-dd HH:mm:ss") ,V:T.value]]
        report.add((vent.displayName):set)
    }
    return report.toString() ?: "nothing new..."
}

/*
	//spit out some time testing...
    def startTime = now() //epocMS, UTC
    def startTimeLocal = startTime + location.timeZone.rawOffset //epocMS, Local TZ
    def startTimeString = new Date(startTime).format("yyyy-MM-dd HH:mm")
    def startTimeStringLocal = new Date(startTimeLocal).format("yyyy-MM-dd HH:mm")
    log.info "times- startTime:${startTime} startTimeString:${startTimeString} startTimeLocal:${startTimeLocal} startTimeStringLocal:${startTimeStringLocal}"


*/
