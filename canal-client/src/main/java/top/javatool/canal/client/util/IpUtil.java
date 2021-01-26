package top.javatool.canal.client.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * @Author: meng.zhao
 * @Desctription:
 * @Date: Created in 2021/1/26 19:33
 * @Version: 1.0
 */
public class IpUtil {

  public static String getLinuxLocalIp(){
    String ip = "";
    try {
      for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
          en.hasMoreElements(); ) {
        NetworkInterface intf = en.nextElement();
        String name = intf.getName();
        if (!name.contains("docker") && !name.contains("lo")) {
          for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
              enumIpAddr.hasMoreElements(); ) {
            InetAddress inetAddress = enumIpAddr.nextElement();
            if (!inetAddress.isLoopbackAddress()) {
              String ipaddress = inetAddress.getHostAddress().toString();
              if (!ipaddress.contains("::") && !ipaddress.contains("0:0:")
                  && !ipaddress.contains("fe80")) {
                ip = ipaddress;
              }
            }
          }
        }
      }
    } catch (SocketException ex) {
      ip = "127.0.0.1";
      ex.printStackTrace();
    }
    return ip;
  }
}
