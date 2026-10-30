package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.*;
import java.io.*;

public class HttpTextTest {
    @Test public void rejectsOversizedBodiesAndRedirects() throws Exception {
        try(Server large=new Server(false,false);Server redirect=new Server(false,true)) {
            assertThrows(IOException.class,()->HttpText.get(large.url()));assertThrows(IOException.class,()->HttpText.get(redirect.url()));
        }
    }
    @Test public void slowTrickleCannotExtendTotalRequestForever() throws Exception {
        try(Server server=new Server(true,false)) {long start=System.nanoTime();
            assertThrows(IOException.class,()->HttpText.get(server.url(),65536,240));
            assertTrue((System.nanoTime()-start)/1_000_000<1500);}
    }
    private static class Server implements AutoCloseable {
        final ServerSocket listener; volatile Socket current;
        Server(boolean slow,boolean redirect)throws IOException {
            listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));
            Thread thread=new Thread(()->{try(Socket socket=listener.accept()) {
                current=socket;BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream()));
                for(String line;(line=input.readLine())!=null&&!line.isEmpty();) { }
                OutputStream out=socket.getOutputStream();
                out.write((redirect?"HTTP/1.1 302 Found\r\nLocation: /other\r\nContent-Length: 0\r\n\r\n"
                        :"HTTP/1.1 200 OK\r\nContent-Length: 70000\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                if(!redirect) {
                    if(!slow)out.write(new byte[70000]);
                    else for(int i=0;i<100;i++){out.write(65);out.flush();Thread.sleep(40);}
                }
            }catch(Exception closed){}});
            thread.setDaemon(true);thread.start();
        }
        String url(){return "http://127.0.0.1:"+listener.getLocalPort()+"/test";}
        public void close()throws IOException {listener.close();if(current!=null)current.close();}
    }
}
