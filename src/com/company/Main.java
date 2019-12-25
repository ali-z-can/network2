package com.company;

import com.sun.deploy.util.ArrayUtil;
import com.sun.xml.internal.ws.api.message.Packet;
import sun.misc.IOUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Main {

    public static void main(String[] args) throws IOException {

        String filePath = args[0];
        int port = Integer.parseInt(args[1]);
        int windowSize = Integer.parseInt(args[2]);
        int timeout = Integer.parseInt(args[3]);
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        InetAddress address = null;
        try {
            address = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        File f1 = new File(filePath);

        PacketHolder ph = new PacketHolder(windowSize,f1,port);

        while(ph.waitingForResponseQ.size() != 0 &&  ph.packetsQ.size()>0){
            if(ph.waitingForResponseQ.size()<=windowSize && ph.packetsQ.size()>0){

                System.out.println("in if");
                DatagramSocket ds = new DatagramSocket();
                SenderRecieverData senderRecieverData = new SenderRecieverData(ph.packetsQ.poll(),ds);
                Sender snd1 = new Sender(senderRecieverData,timeout);
                Reciever reciever = new Reciever(ph,senderRecieverData);
                snd1.start();
                reciever.start();
            }

        }
    }
}

class Sender extends Thread{
    SenderRecieverData senderRecieverData;
    int timeOut;
    public Sender(SenderRecieverData senderRecieverData,int timeOut){
        this.senderRecieverData = senderRecieverData;
        this.timeOut = timeOut;
    }
    @Override public void run(){


            do {

                try {
                    System.out.println("im fucking sending");
                    senderRecieverData.clientSocket.send(senderRecieverData.datagramPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    Thread.sleep(timeOut);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


            } while (!senderRecieverData.recieved);



    }


}

class Reciever extends Thread{

    PacketHolder p;
    SenderRecieverData senderRecieverData;
    public Reciever(PacketHolder p,SenderRecieverData senderRecieverData) throws SocketException {
        this.p = p;
        this.senderRecieverData = senderRecieverData;
    }
    @Override
    public void run(){
        System.out.println("in receiver run");

        synchronized (senderRecieverData){


            System.out.println(" here in lock " );

            byte[] buffer = new byte[2];
            DatagramPacket recievedPaket = new DatagramPacket(buffer,2);
                try {
                    System.out.println(" here trying to recieve " );
                    //initialize recievedPaket with server response
                    senderRecieverData.clientSocket.receive(recievedPaket);
                    senderRecieverData.recieved = true;
                    System.out.println(" here received " );

                } catch (IOException e) {
                    System.out.println("in except");
                    e.printStackTrace();
                }



                synchronized (p){
                    DatagramPacket datagramPacket = p.waitingForResponseQ.peek();
                    while (datagramPacket != senderRecieverData.datagramPacket){
                        try {
                            p.wait();
                            datagramPacket = p.waitingForResponseQ.peek();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    p.waitingForResponseQ.poll();
                    p.notifyAll();
                }


        }

    }


}
class SenderRecieverData{
    DatagramSocket clientSocket;
    boolean recieved  = false;
    DatagramPacket datagramPacket;
    public SenderRecieverData(DatagramPacket datagramPacket,    DatagramSocket clientSocket){
        this.datagramPacket = datagramPacket;
        this.clientSocket = clientSocket;
    }
}

class PacketHolder{


    BlockingQueue<DatagramPacket> packetsQ;
    BlockingQueue<DatagramPacket> waitingForResponseQ;


    public int wftS(){
        return waitingForResponseQ.size();
    }

    public PacketHolder(int windowSize,File f,int port) throws IOException {

            byte [] bytes = Files.readAllBytes(f.toPath());
            int last_package_size = bytes.length % 1022;
            int packetCount = (bytes.length - last_package_size) / 1022;
            waitingForResponseQ = new ArrayBlockingQueue<>(windowSize);
            if(last_package_size == 0){
                packetsQ = new ArrayBlockingQueue<>(packetCount);
            }else {
                packetsQ = new ArrayBlockingQueue<>(packetCount+1);
            }
            System.out.println("packet count = "+packetCount);
            for(int counter = 0; counter<packetCount;counter++){
                byte val[] = new byte[]{
                        (byte)(counter>>8),
                        (byte)(counter)
                };
                byte[] valss = new byte[1022];
                System.arraycopy(bytes,counter*1022,valss,0,1022);
                byte[] buf = new byte[1024];
                System.arraycopy(val,0,buf,0,2);
                System.arraycopy(valss,0,buf,2,1022);
                packetsQ.add(new DatagramPacket(buf,1024,InetAddress.getLocalHost(),port));

                //System.out.println(packetsQ);
            }

        if(last_package_size == 0){
            //do nothing
        }else {
            byte val[] = new byte[]{
                    (byte)(packetCount>>8),
                    (byte)(packetCount)
            };
            byte[] valss = new byte[last_package_size];
            System.arraycopy(bytes,packetCount*1022,valss,0,last_package_size);
            byte[] buf = new byte[last_package_size+2];
            System.arraycopy(val,0,buf,0,2);
            System.arraycopy(valss,0,buf,2,last_package_size);
            packetsQ.add(new DatagramPacket(buf,last_package_size+2,InetAddress.getLocalHost(),port));
            try{
                waitingForResponseQ.add(new DatagramPacket(buf,last_package_size+2,InetAddress.getLocalHost(),port));

            }catch (IllegalStateException e ){
                System.out.println("well we are full so here is the error message(nothing to do everything is normal) "+e.getMessage());
            }


        }
        //seems okey till here
        System.out.println(packetsQ);


    }

}


