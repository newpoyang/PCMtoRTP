package com.xbs.pcmtortp;

import android.app.Activity;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;


import com.xbs.rtp.MutliRtpSocket;
import com.xbs.rtp.RtpPacket;
import com.xbs.rtp.RtpSocket;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.ArrayBlockingQueue;

public class AudioTrack extends Activity {
    RtpSocket rtp_socket = null;
    RtpPacket rtp_packet;
    private String TAG = "playRecordTest";
    private android.media.AudioTrack track;
    private AudioManager am;
    int oneFrameSize = 1024, min;
    private boolean isRecording = false;
    private DatagramSocket socket;
    boolean isRunning = false;
    private Boolean isWriteFile = false;//是否写成wav文件
    private String FILE_NAME, wavFilePath;//文件目录
    private int audioSampleRate = 8000;//采样率
    private int port = 34567;//端口
    private String IP = "224.168.168.168"; //rtp的目的ip

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track);
        Button btTrack = findViewById(R.id.track);
        btTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });
    }


    public void start(){
        FILE_NAME = getExternalFilesDir(null) + "/track.pcm";
        wavFilePath = getExternalFilesDir(null) + "/track.wav";

        min = AudioRecord.getMinBufferSize(audioSampleRate,
                AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        track = new android.media.AudioTrack(AudioManager.STREAM_MUSIC, audioSampleRate,
                AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT,
                min, android.media.AudioTrack.MODE_STREAM);

        track.play();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FileOutputStream os = null;
                    if (isWriteFile) {
                        File file = new File(FILE_NAME);
                        try {
                            if (!file.exists()) {
                                file.createNewFile();
                                Log.i(TAG, "创建文件->" + FILE_NAME);
                            }
                            os = new FileOutputStream(file);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    //单播模式
                    if (socket == null) {
                        socket = new DatagramSocket(12345);//本地端口
                    }
                    rtp_socket = new RtpSocket(socket, InetAddress
                            .getByName(IP), port);//rtp远程端口和ip

                    //组播模式
//                    socket = new MulticastSocket(port);
//                    InetAddress inetAddress = InetAddress.getByName(IP);
//                    socket.joinGroup(inetAddress);
//                    Log.e(TAG, "init UdpMultiReceive: ");
//                    rtp_socket = new MutliRtpSocket(socket);

                    byte[] buffer = new byte[oneFrameSize+12];
                    rtp_packet = new RtpPacket(buffer, 0);
                    isRunning = true;

                    while (isRunning){
                        try {
                            //Log.i(TAG, "录制状态->" + isRecording);
                            rtp_socket.receive(rtp_packet);
                            track.write(rtp_packet.getPayload(), 0, rtp_packet.getPayload().length);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (isWriteFile) {
                            if (os != null) {
                                try {
                                    os.write(rtp_packet.getPayload());
                                    Log.i(TAG, "写数据->" + rtp_packet.getPayload().length);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                    if (isWriteFile) {
                        try {
                            os.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        PcmToWavUtil ptwUtil = new PcmToWavUtil(audioSampleRate, AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT);
                        ptwUtil.pcmToWav(FILE_NAME, wavFilePath, false);
                    }
                } catch (SocketException e) {
                    e.printStackTrace();
                }  catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (track != null){
            track.stop();
            track.release();
        }
        if (rtp_socket != null){
            rtp_socket.close();
            rtp_socket = null;
        }
    }
}
