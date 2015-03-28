package com.neat.pinboard;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import android.app.DownloadManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;
import android.widget.RemoteViews;

import javax.xml.transform.ErrorListener;

public class BleService extends Service{
	private NotificationManager mNM;
	BluetoothManager manager;
    BluetoothAdapter adapter;
    MediaPlayer player;
    Notification mnotification;
    Context mContext;
    String content[] = new String[256];
    private LinkedList<BluetoothGatt> gattList = new LinkedList<BluetoothGatt>();
    int mId = 0;
    private HashMap<String, SparseArray<Content> > devicemap = new HashMap<String, SparseArray<Content> >();
    private HashMap<String, Image > deviceImageMap = new HashMap<String, Image >();
    private HashMap<String, UpdateDevice> updateMap =new  HashMap<String, UpdateDevice>();
    Timer timer;
    private RequestQueue queue;
    //boolean update = false;

    Response.ErrorListener errorListener = new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            Log.d("upload","error" );
        }
    };

    Response.Listener ResponseHandler = new Response.Listener<String>() {
        @Override
        public void onResponse(String response) {
            Log.d("upload","response:"+response );
            JSONObject obj;
            try {
                obj = new JSONObject(response);


                JSONArray arr = obj.getJSONArray("array");
                for (int i = 0; i < arr.length(); i++)
                {
                    String tmpaddr = arr.getJSONObject(i).getString("address");
                    if(updateMap.get(tmpaddr)!=null){
                        UpdateDevice updateDevice = updateMap.get(tmpaddr);
                        BluetoothDevice tmpDevice = updateDevice.device;
                        updateDevice.updateList.add(arr.getJSONObject(i).getString("content"));
                        gattList.add( tmpDevice.connectGatt(mContext, false, newBleCallback() ) );
                    }

                }

            } catch (JSONException e) {

                e.printStackTrace();
            }



        }


    };
    
    class UpdateTask implements Runnable {

        LinkedList<String> dataList;
    	BluetoothGatt gatt;
    	
    	UpdateTask(LinkedList<String> dataList,BluetoothGatt gatt) {
        	this.dataList = dataList;
    		this.gatt = gatt; 
    		
        }

		@Override
		public void run() {
			UUID serviceUUID = UUID.fromString("00002225-0000-1000-8000-00805f9b34fb");
			BluetoothGattService tempService = gatt.getService(serviceUUID);
			BluetoothGattCharacteristic characteristic = tempService.getCharacteristic(UUID.fromString("00002226-0000-1000-8000-00805f9b34fb"));
			for(int j=0;j<dataList.size();j++){
                String data = dataList.get(j);
                int max =  (int) Math.ceil((double)data.length()/20);
                for(int i=0;i<max;i++){

                    if(i == max-1){
                        String subdata = data.substring(i*20, data.length() );
                        byte bdata[] = subdata.getBytes();
                        characteristic.setValue(bdata);
                        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        gatt.writeCharacteristic(characteristic);

                        byte endByte[]={(byte)0x00};
                        characteristic.setValue(endByte);
                        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        gatt.writeCharacteristic(characteristic);

                    }else{
                        String subdata = data.substring(i*20, (i+1)*20);
                        byte bdata[] = subdata.getBytes();
                        characteristic.setValue(bdata);
                        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        gatt.writeCharacteristic(characteristic);
                    }
                }
            }

			try {
	            Thread.sleep(1000);
	         } catch (Exception e) {
	            System.out.println(e);
	         }
			Log.d("response", "thread");
            dataList.clear();
			gatt.disconnect();
			new ResetScanTask().execute(true);

            //setsuccess
            queue.add(new StringRequest(Request.Method.GET,"http://neatxiboard.appspot.com/upload?addr="+gatt.getDevice().getAddress(), new Response.Listener<String>() {
                @Override
                public void onResponse(String s) {

                }
            },errorListener) );
            //setsuccess

            try {
                Thread.sleep(10000);
            } catch (Exception e) {
                System.out.println(e);
            }

            updateMap.remove(gatt.getDevice().getAddress());

		}
    	
    	
    }
    
    class HandlePacketTask implements Runnable {
    	byte[] scanRecord;
    	String address;
    	
        HandlePacketTask(String address,byte[] scanRecord) {
        	this.address = address;
    		this.scanRecord = scanRecord; 
        }
        public int covert(int a){
            if(a<0){
                a=a+256;
            }
            return a;
        }
        public void run() {
        	String show = null;
            boolean lastFrag = false;
        	String packet = "";
            int tempTotalPacket = covert(scanRecord[11]);
            int tempCurrentPacket = covert(scanRecord[10]);
            int contentNum = Math.abs(scanRecord[9]);

        	for(int i=12;i<30;i++){
        		packet += String.valueOf((char)scanRecord[i]);
        	}
        	if(devicemap.get(address) == null){
        		SparseArray<Content> templist = new SparseArray<Content>();
                deviceImageMap.put(address,new Image());
        		devicemap.put(address, templist);
        	}


            if(scanRecord[9]==-1){
                Image tempImage = deviceImageMap.get(address);
                tempImage.setTotalImagePacket(tempTotalPacket);
                tempImage.setImgPacket(tempCurrentPacket, scanRecord);

            }else{
                SparseArray<Content> templist = devicemap.get(address);
                if(templist.get(contentNum) == null){
                    Content tempcontent = new Content();
                    templist.put(contentNum,tempcontent);
                }
                Content content;
                if(scanRecord[9]<0){
                    content = templist.get(contentNum);
                    content.setTotalImagePacket(tempTotalPacket);
                    lastFrag = content.setImgPacket(tempCurrentPacket, scanRecord);
                }else{
                    content = templist.get(contentNum);
                    content.setTotalpacket(tempTotalPacket);
                    lastFrag = content.setpacket(tempCurrentPacket, packet);
                }
                Image tempImage = deviceImageMap.get(address);
                Log.d("who","img:"+String.valueOf(tempImage.checkComplete()));
                Log.d("who","content:"+String.valueOf(content.checkComplete()));
                Log.d("who","show:"+String.valueOf(content.getShowed()));

                if(content.checkComplete() && tempImage.checkComplete() && content.getShowed()==false){
                    content.setShowed(true);
                    showNotification(content.getFullcontent(), tempImage.getImage());
                }
            }



        }
    }
	
	public class BleBinder extends Binder {
		BleService getService() {
            return BleService.this;
        }
    }
	private class ResetScanTask extends AsyncTask<Boolean, Integer, Boolean>
	{
	    
		@Override
		protected Boolean doInBackground(Boolean... flag) {
			
			adapter.stopLeScan(mDeviceFoundCallback);
			UUID[] mServicesToSearchDeviceFor = {
					UUID.fromString("00002225-0000-1000-8000-00805f9b34fb")
	        };
        	adapter.startLeScan(mServicesToSearchDeviceFor,mDeviceFoundCallback);
			return true;

		}
	    
		@Override
	    protected void onProgressUpdate(Integer... progress)
	    {
	        
	    }
		@Override
	    protected void onPostExecute(Boolean result)
	    {
	       
	    }

		
	}
	
	private final IBinder mBinder = new BleBinder();

	@Override
	public IBinder onBind(Intent intent) {
		
		return null;
	}
	
	private BluetoothAdapter.LeScanCallback mDeviceFoundCallback = new BluetoothAdapter.LeScanCallback() {
		
        @Override // comes from: startLeScan
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {

        	String msg = "payload = ";
        	
        	for (byte b : scanRecord)
        	  msg += String.format("%02x ", b);
        	//Log.d("scanRecord",msg);
        	
        	if(updateMap.get(device.getAddress())==null){
                Log.d("upload",device.getAddress());
                updateMap.put(device.getAddress(),new UpdateDevice(device));
                queue.add(new StringRequest(Request.Method.GET, "http://neatxiboard.appspot.com/list?addr="+device.getAddress(),ResponseHandler, errorListener) );
                queue.start();
        	}
        	
        	Log.d("address",device.getAddress() );
        	new ResetScanTask().execute(true);
        	new Thread(new HandlePacketTask(device.getAddress(),scanRecord)).start();//adv

        	
        }
    };
    public BluetoothGattCallback newBleCallback(){
    	BluetoothGattCallback mBleCallback = new BluetoothGattCallback(){
//    		private byte totalPackets = 0;
//    		private byte recievePackets = 0;
    		String content = "";
        	@Override // comes from: connectGatt
            public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
    			
    			if (newState == BluetoothProfile.STATE_CONNECTED) {
    				gatt.discoverServices();

                }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                	
                }

    		}
        	
        	@Override // comes from: discoverServices()
            public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
    			
    			
    			if (status == BluetoothGatt.GATT_SUCCESS){
    				Log.d("service ", " discover" );
					new Thread(new UpdateTask(updateMap.get(gatt.getDevice().getAddress()).updateList,gatt)).start();
    				
    				
    			}
    		}
        	@Override // comes from: setCharacteristicNotification
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        		byte a[] = characteristic.getValue();
        		
            	String data = characteristic.getStringValue(2);
            	Log.d("notify",data);
            	content+=data;
            	if(a[0]==a[1]){
            		gatt.disconnect();
                	//showNotification(content);
            	}
            	
            }
        	
        };
    	return mBleCallback;
    }
	@Override
    public void onCreate() {
        Log.d("service","start");
		super.onCreate();
        registerReceiver(serviceBroadcastReceiver, new IntentFilter("checkService"));
		mContext = this.getApplicationContext();
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();
        
        Intent mainIntent = new Intent(getApplicationContext(), MainActivity.class);
        mainIntent.putExtra("serviceStart", true);
        
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
        		mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("SmartAD is running...")
                .setContentText("");
        mBuilder.setContentIntent(pi);
        Notification mnotification = mBuilder.build();
        
        startForeground(12, mnotification);
        
        
        queue = Volley.newRequestQueue(this);

        /*TimerTask pollingTask = new TimerTask(){
        	@Override
        	public void run() {
        		Log.d("timer","run" );
        		queue.add(stringRequest);
        		queue.start();
        	}
    	};
        // Add the request to the RequestQueue.
        timer =new Timer();
        timer.schedule(pollingTask, 0, 10000);*/
        
        
//        showNotification(R.drawable.heart,"�R�߸q��","12/15~12/20���ʤ��߼s��");
//        showNotification(R.drawable.cousins,"�D�����Z","���p�� ����:180 �魫:75 ���:...");
//        showNotification(R.drawable.steak,"��������","�t�ϯS�\12/20�}�� �u�n129");
        
    }
	

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		UUID[] mServicesToSearchDeviceFor = {
				UUID.fromString("00002225-0000-1000-8000-00805f9b34fb")
        };
		adapter.startLeScan(mServicesToSearchDeviceFor,mDeviceFoundCallback);
		
		//player.start();
	    return START_STICKY;
	}
	@Override
	public void onDestroy() {
        unregisterReceiver(serviceBroadcastReceiver);
		adapter.stopLeScan(mDeviceFoundCallback);
		for(int i=0;i<gattList.size();i++){
			gattList.pop().disconnect();
		}
		//timer.cancel();
	}
	private void showNotification(String content,Bitmap bitmap) {
		
		String subcontent[] = content.split("##");

		RemoteViews views = new RemoteViews(getPackageName(), R.layout.notify);  
		views.setTextViewText(R.id.textView2, subcontent[0]);
        views.setImageViewBitmap(R.id.imageView1,bitmap);
		NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.icon)
                .setAutoCancel(true)
                .setContent(views)
                ;
		Intent resultIntent;
		if(subcontent.length<2){
			resultIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/"));
		}else{
			resultIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(subcontent[1]));
		}

        try {
            File myFile = new File(Environment.getExternalStorageDirectory()+"/pinboard", "history.txt");

            FileInputStream stream = new FileInputStream(myFile);
            String jsonStr = null;
            try {
                FileChannel fc = stream.getChannel();
                MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());

                jsonStr = Charset.defaultCharset().decode(bb).toString();
            }
            finally {
                stream.close();
            }

            JSONObject jsonObj = new JSONObject(jsonStr);

            // Getting data JSON Array nodes
            JSONArray data  = jsonObj.getJSONArray("history");
            JSONObject newJsonObj = new JSONObject();
            newJsonObj.put("content",subcontent[0]);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            String currentDateandTime = sdf.format(new Date());
            newJsonObj.put("date",currentDateandTime);
            data.put(newJsonObj);

            FileWriter fw = new FileWriter(myFile,false);
            fw.write(jsonObj.toString());
            fw.close();



        } catch (Exception e) {
            e.printStackTrace();
        }


		
        


        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);

        stackBuilder.addParentStack(MainActivity.class);

        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                    0,
                    PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        

        mId++;
        Notification mnotify =  mBuilder.build();
        mnotify.bigContentView = views;
        mNotificationManager.notify(mId ,mnotify);
    }
	
	private void showNotification(int id,String title,String content) {

		
		NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                .setSmallIcon(id)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(content)
                ;
		
		
        Intent resultIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.8pot.com.tw/"));


        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);

        stackBuilder.addParentStack(MainActivity.class);

        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                    0,
                    PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        

        mId++;
        Notification mnotify =  mBuilder.build();
       
        mNotificationManager.notify(mId ,mnotify);
    }

    private BroadcastReceiver serviceBroadcastReceiver =  new BroadcastReceiver() {
        private final static String MY_MESSAGE = "checkService";
        @Override
        public void onReceive(Context mContext, Intent mIntent) {
            Log.d("Receiver",mIntent.getAction());
            if(MY_MESSAGE.equals(mIntent.getAction())){
                Intent ServiceIsStart = new Intent();
                ServiceIsStart.setAction("checkService.true");
                sendBroadcast(ServiceIsStart);
            }
        }
    };

	

}
