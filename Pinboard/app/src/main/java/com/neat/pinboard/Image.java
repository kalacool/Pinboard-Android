package com.neat.pinboard;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.SparseArray;

import java.util.LinkedList;

/**
 * Created by SAM on 2015/3/24.
 */
public class Image {

    SparseArray<LinkedList<Byte> > ImgArray = new SparseArray<LinkedList<Byte>>();
    private int totalImagePacket;
    public Image(){
        totalImagePacket =-1;
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
    public boolean checkComplete(){
        if(ImgArray.size() == totalImagePacket){
            return true;
        }else{
            return false;
        }

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
