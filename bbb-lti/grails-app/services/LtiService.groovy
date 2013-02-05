/* 
    BigBlueButton open source conferencing system - http://www.bigbluebutton.org/

    Copyright (c) 2012 BigBlueButton Inc. and by respective authors (see below).

    This program is free software; you can redistribute it and/or modify it under the
    terms of the GNU Lesser General Public License as published by the Free Software
    Foundation; either version 3.0 of the License, or (at your option) any later
    version.

    BigBlueButton is distributed in the hope that it will be useful, but WITHOUT ANY
    WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
    PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License along
    with BigBlueButton; if not, see <http://www.gnu.org/licenses/>.
*/

import java.util.Map;

import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import org.apache.commons.codec.binary.Base64

class LtiService {

    boolean transactional = false
    
    def endPoint = "http://192.168.0.153/lti/tool.xml"
    def consumers = "demo:welcome"
    Map<String, String> consumerMap
    
    private Map<String, String> getConsumer(consumerId) {
        Map<String, String> consumer = null
        
        if( this.consumerMap.containsKey(consumerId) ){
            consumer = new HashMap<String, String>()
            consumer.put("key", consumerId);
            consumer.put("secret",  this.consumerMap.get(consumerId))
        }
        
        return consumer
    }

    private void initConsumerMap(){
        this.consumerMap = new HashMap<String, String>()
        String[] consumers = this.consumers.split(",")
        for( int i=0; i < consumers.length; i++){
            String[] consumer = consumers[i].split(":")
            if( consumer.length == 2 ){
                this.consumerMap.put(consumer[0], consumer[1])
            }
        }
        
    }
    
    public String sign(String sharedSecret, String data) throws Exception
    {
        Mac mac = setKey(sharedSecret)
        
        // Signed String must be BASE64 encoded.
        byte[] signBytes = mac.doFinal(data.getBytes("UTF8"));
        String signature = encodeBase64(signBytes);
        return signature;
    }
    
    private Mac setKey(String sharedSecret) throws Exception
    {
        Mac mac = Mac.getInstance("HmacSHA1");
        byte[] keyBytes = sharedSecret.getBytes("UTF8");
        SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA1");
        mac.init(signingKey);
        return mac
    }

    private String encodeBase64(byte[] signBytes) {
        return Base64.encodeBase64URLSafeString(signBytes)
    }
}
