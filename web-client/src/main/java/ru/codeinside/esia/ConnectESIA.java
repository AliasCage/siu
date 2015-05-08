
package ru.codeinside.esia;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.ws.Holder;
import javax.xml.ws.RequestWrapper;
import javax.xml.ws.ResponseWrapper;


/**
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 2.2.9-b130926.1035
 * Generated source version: 2.2
 * 
 */
@WebService(name = "ConnectESIA", targetNamespace = "http://oep-penza.ru/com/oep/esia")
@XmlSeeAlso({
    ObjectFactory.class
})
public interface ConnectESIA {


    /**
     * 
     * @param message
     * @param messageData0
     * @param messageData
     * @throws Exception_Exception
     */
    @WebMethod(action = "urn:getConnectESIA")
    @RequestWrapper(localName = "getConnectESIA", targetNamespace = "http://oep-penza.ru/com/oep/esia", className = "ru.codeinside.esia.GetConnectESIA")
    @ResponseWrapper(localName = "getConnectESIAResponse", targetNamespace = "http://smev.gosuslugi.ru/rev120315", className = "ru.codeinside.esia.DefaultResponseWrapper")
    public void getConnectESIA(
        @WebParam(name = "Message", targetNamespace = "http://smev.gosuslugi.ru/rev120315", mode = WebParam.Mode.INOUT)
        Holder<MessageType> message,
        @WebParam(name = "MessageData", targetNamespace = "http://smev.gosuslugi.ru/rev120315")
        MessageConnectESIAData messageData,
        @WebParam(name = "MessageData", targetNamespace = "http://smev.gosuslugi.ru/rev120315", mode = WebParam.Mode.OUT)
        Holder<ResultMessageDataType> messageData0)
        throws Exception_Exception
    ;

}
