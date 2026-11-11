package com.example.simplemusicplayer.util;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

import com.example.simplemusicplayer.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for audio output device discovery, labeling, and icon resolution.
 */
public final class AudioDeviceHelper {

    private AudioDeviceHelper() {
        // Utility class, do not instantiate
    }

    /**
     * Retrieves all available audio output devices connected to the device.
     */
    public static List<AudioDeviceInfo> getAvailableAudioDevices(Context context) {
        List<AudioDeviceInfo> outputDevices = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null) {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                for (AudioDeviceInfo dev : devices) {
                    int type = dev.getType();
                    if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                            type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                            type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                            type == AudioDeviceInfo.TYPE_LINE_ANALOG) {
                        outputDevices.add(dev);
                    }
                }
            }
        }
        return outputDevices;
    }

    /**
     * Resolves human-readable Vietnamese name for an audio device.
     */
    public static String getDeviceTypeName(AudioDeviceInfo device) {
        if (device == null) {
            return "Mặc định (Loa máy)";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            CharSequence name = device.getProductName();
            if (name != null && name.length() > 0 && !name.toString().trim().equalsIgnoreCase("Speaker")) {
                return name.toString().trim();
            }
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                    return "Loa ngoài điện thoại";
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                    return "Tai nghe có dây";
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                    return "Tai nghe / Loa Bluetooth";
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    return "Thiết bị âm thanh USB-C";
                case AudioDeviceInfo.TYPE_LINE_ANALOG:
                    return "Cáp âm thanh AUX";
                default:
                    return "Thiết bị âm thanh ngoài";
            }
        }
        return "Loa thiết bị";
    }

    /**
     * Resolves descriptive connection subtitle for the device selector list.
     */
    public static String getDeviceDescription(AudioDeviceInfo device) {
        if (device == null) return "Theo kết nối hiện hành";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                    return "Loa tích hợp sẵn trên máy";
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                    return "Tai nghe / Loa Bluetooth không dây";
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                    return "Cổng cắm tai nghe 3.5mm";
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    return "Cổng USB-C / Bộ giải mã DAC";
                default:
                    return "Đang kết nối";
            }
        }
        return "Đang kết nối";
    }

    /**
     * Resolves drawable icon for an audio device.
     */
    public static int getDeviceIcon(AudioDeviceInfo device) {
        if (device == null) {
            return R.drawable.ic_speaker;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                    return R.drawable.ic_speaker;
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                    return R.drawable.ic_headphones;
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                    return R.drawable.ic_bluetooth;
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    return R.drawable.ic_audio_output;
                default:
                    return R.drawable.ic_audio_output;
            }
        }
        return R.drawable.ic_speaker;
    }

    /**
     * Returns the currently active audio device (prioritizing Bluetooth or wired headsets over speaker).
     */
    public static AudioDeviceInfo getActiveConnectedDevice(Context context, List<AudioDeviceInfo> devices) {
        if (devices == null || devices.isEmpty()) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (AudioDeviceInfo dev : devices) {
                if (dev.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        dev.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        dev.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        dev.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
                    return dev;
                }
            }
        }
        return null;
    }
}
