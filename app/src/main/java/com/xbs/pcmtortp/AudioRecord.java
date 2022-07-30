package com.xbs.pcmtortp;

import android.app.Activity;
import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;


import com.xbs.rtp.RtpPacket;
import com.xbs.rtp.RtpSocket;
import com.xbs.rtp.RtpStreamSenderNew_SDK16;

;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class AudioRecord extends Activity {
    private String TAG = "audioRecordTest";
    private android.media.AudioRecord record = null;
    private Thread recordingThread;
    private boolean isRecording = false;
    private int min;
    private RtpSocket rtp_socket;
    private DatagramSocket socket;
    private int audioSampleRate = 8000;//采样率
    private Boolean isWriteFile = true;//是否写成wav文件
    private String FILE_NAME, wavFilePath;//文件目录
    private int oneFrameSize = 160;//每个包大小
    private String IP = "224.168.168.168"; //rtp的目的ip
    private int port = 34567;//端口

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);
        init();
        Button btRecord = findViewById(R.id.record);
        btRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecord();
            }
        });
    }

    public void init(){
        min = android.media.AudioRecord.getMinBufferSize(audioSampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        record = new android.media.AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                audioSampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                min);
        FILE_NAME = getExternalFilesDir(null) + "/record.pcm";
        wavFilePath = getExternalFilesDir(null) + "/record.wav";
        if (Build.VERSION.SDK_INT >= 16) {
            RtpStreamSenderNew_SDK16.aec(record);//消回音降噪音
        }
    }

    public void startRecord() {
        if (isRecording) {
            return;
        }
        isRecording = true;
        record.startRecording();
        Log.i(TAG, "开始录音");
        recordingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte data[] = new byte[oneFrameSize];
                FileOutputStream os = null;
                if (isWriteFile) {
                    File file = new File(FILE_NAME);
                    try {
                        if (!file.exists()) {
                            file.createNewFile();
                            Log.i(TAG, "创建录音文件->" + FILE_NAME);
                        }
                        os = new FileOutputStream(file);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                int seqn = 0;
                long time = 0;
                byte[] buffer = new byte[oneFrameSize + 12];
                RtpPacket rtp_packet = new RtpPacket(buffer, 0);
                rtp_packet.setPayloadType(8);

                try {
                    if (socket == null) {
                        socket = new DatagramSocket(12346);//本地端口
                    }
                    rtp_socket = new RtpSocket(socket, InetAddress
                            .getByName(IP), port);//rtp远程端口和ip
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }

                int read;
                while (isRecording) {
                    read = record.read(data, 0, oneFrameSize);
                    if (android.media.AudioRecord.ERROR_INVALID_OPERATION != read) {
                        if (os != null && isWriteFile) {
                            try {
                                os.write(data);
                                Log.i(TAG, "写录音数据->" + read);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    rtp_packet.setSequenceNumber(seqn++);
                    rtp_packet.setTimestamp(time);
                    rtp_packet.setPayloadLength(read);
                    rtp_packet.setPayload(data, read);
                    try {
                        rtp_socket.send(rtp_packet);
                        //rtp_packet.printString();
                        Log.i(TAG, "发送rtp包->" + read);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    time += oneFrameSize;
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
            }
        });
        recordingThread.start();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRecording = false;
        if (record != null) {
            record.stop();
            record.release();
            record = null;
            recordingThread = null;
        }
        if (rtp_socket != null){
            rtp_socket.close();
            rtp_socket = null;
            socket.close();
        }
    }
}
