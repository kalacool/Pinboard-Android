package com.neat.pinboard;

import java.util.LinkedList;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.SparseArray;

public class Content {
	private int totalpacket;
    private int totalImagePacket;
    private String fullcontent;
	SparseArray<String> packetList = new SparseArray<String>();
    SparseArray<LinkedList<Byte> > ImgArray = new SparseArray<LinkedList<Byte>>();

	public Content(){
        totalpacket = -1;
        totalImagePacket =-1;
        fullcontent="";
    }
    public void setTotalpacket(int totalpacket){
        if(this.totalpacket==-1) {
            this.totalpacket = totalpacket;
        }
    }
    public void setTotalImagePacket(int totalImagePacket){
        if(this.totalImagePacket==-1) {
            this.totalImagePacket = totalImagePacket;
        }
    }
    public boolean setImgPacket(int num,byte packet[]){

        if(ImgArray.size() == totalImagePacket || ImgArray.get(num-1) !=null ){
            return false;
        }

        LinkedList<Byte> imgfrag = new LinkedList<Byte>();

        for(int i=12;i<=29;i++){
            imgfrag.add(new Byte(packet[i]) );
        }
        ImgArray.put(num-1,imgfrag);
        return  checkComplete();

    }
	public boolean setpacket(int num,String packet){
		if(packetList.size() == totalpacket || packetList.get(num-1) !=null ){
			return false;
		}
		packetList.put(num-1, packet);
        return  checkComplete();
		
	}
    private boolean checkComplete(){
        if(packetList.size() == totalpacket && ImgArray.size() == totalImagePacket){
            return true;
        }else{
            return false;
        }

    }


    public String getFullcontent(){
        for(int i=0;i<packetList.size();i++){
            fullcontent  += packetList.get(i);
        }

        return fullcontent;
    }
    public Bitmap getImage(){
        Bitmap bitmap;
        byte []byteArray=new byte[totalImagePacket*18];
        int count=0;
        for(int i=0;i<ImgArray.size();i++){
            LinkedList<Byte> ByteList = ImgArray.get(i);
            for (int j=0;j<ByteList.size();j++) {
                byteArray[count] = ByteList.get(j).byteValue();
                count++;
            }

        }
        bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);


        return bitmap;
    }

}
