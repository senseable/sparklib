// ISparkService.aidl
package eu.senseable.service;
import eu.senseable.service.ISparkCallback;

interface ISparkService {
    void setAddr(String addr);
    void setEvents(inout List<String> list);

    String getAddr();
    boolean getBrownout();
    List<String> getEvents();
    int getStatus();

    void register(ISparkCallback c);
    void unregister(ISparkCallback c);
}
