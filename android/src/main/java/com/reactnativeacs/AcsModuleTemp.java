package com.reactnativeacs;

import androidx.annotation.NonNull;

import android.os.AsyncTask;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;

import com.acs.smartcard.Reader;
import com.acs.smartcard.Reader.OnStateChangeListener;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.widget.ArrayAdapter;

@ReactModule(name = AcsModuleTemp.NAME)
public class AcsModuleTemp extends ReactContextBaseJavaModule {
  public static final String NAME = "Acs";
  private static final String ACTION_USB_PERMISSION = "com.reactnativeacs.USB_PERMISSION";
  private static final String[] powerActionStrings = {"Power Down",
    "Cold Reset", "Warm Reset"};

  private static final String[] stateStrings = {"Unknown", "Absent",
    "Present", "Swallowed", "Powered", "Negotiable", "Specific"};


  private ReactApplicationContext reactContext;
  private UsbManager mManager;
  private Reader mReader;
  private PendingIntent mPermissionIntent;
  private ArrayAdapter<String> mReaderAdapter;
  private ArrayAdapter<String> mSlotAdapter;


  public AcsModuleTemp(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }


  private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

    public void onReceive(Context context, Intent intent) {

      String action = intent.getAction();

      if (ACTION_USB_PERMISSION.equals(action)) {

        synchronized (this) {

          UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

          if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {

            if (device != null) {

              // Open reader
              logMsg("Opening reader: " + device.getDeviceName()
                + "...");
              new OpenTask().execute(device);
            }

          } else {
            logMsg("Permission denied for device "
              + device.getDeviceName());
            ;
          }
        }

      } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {

        synchronized (this) {

          // Update reader list
          mReaderAdapter.clear();
          for (UsbDevice device : mManager.getDeviceList().values()) {
            logMsg(device.getDeviceName());
            if (mReader.isSupported(device)) {
              mReaderAdapter.add(device.getDeviceName());
            }
          }

          UsbDevice device = (UsbDevice) intent
            .getParcelableExtra(UsbManager.EXTRA_DEVICE);

          if (device != null && device.equals(mReader.getDevice())) {

            // Clear slot items
            mSlotAdapter.clear();

            // Close reader
            logMsg("Closing reader...");
            new CloseTask().execute();
          }
        }
      }
    }
  };

  private class OpenTask extends AsyncTask<UsbDevice, Void, Exception> {

    @Override
    protected Exception doInBackground(UsbDevice... params) {

      Exception result = null;

      try {

        mReader.open(params[0]);

      } catch (Exception e) {

        result = e;
      }

      return result;
    }

    @Override
    protected void onPostExecute(Exception result) {

      if (result != null) {

        logMsg(result.toString());

      } else {

        logMsg("Reader name: " + mReader.getReaderName());

        int numSlots = mReader.getNumSlots();
        logMsg("Number of slots: " + numSlots);

        // Add slot items
        mSlotAdapter.clear();
        for (int i = 0; i < numSlots; i++) {
          mSlotAdapter.add(Integer.toString(i));
        }
      }
    }
  }

  private class CloseTask extends AsyncTask<Void, Void, Void> {

    @Override
    protected Void doInBackground(Void... params) {

      mReader.close();
      return null;
    }
  }

  private class PowerParams {
    public int slotNum;
    public int action;
  }

  private class PowerResult {
    public byte[] atr;
    public Exception e;
  }

  private class PowerTask extends AsyncTask<PowerParams, Void, PowerResult> {
    @Override
    protected PowerResult doInBackground(PowerParams... params) {

      PowerResult result = new PowerResult();

      try {

        result.atr = mReader.power(params[0].slotNum, params[0].action);

      } catch (Exception e) {

        result.e = e;
      }

      return result;
    }

    @Override
    protected void onPostExecute(PowerResult result) {

      if (result.e != null) {

        logMsg(result.e.toString());

      } else {

        // Show ATR
        if (result.atr != null) {

          logMsg("ATR:");
          logBuffer(result.atr, result.atr.length);

        } else {

          logMsg("ATR: None");
        }
      }
    }
  }

  private class SetProtocolParams {

    public int slotNum;
    public int preferredProtocols;
  }

  private class SetProtocolResult {

    public int activeProtocol;
    public Exception e;
  }

  private class SetProtocolTask extends
    AsyncTask<SetProtocolParams, Void, SetProtocolResult> {

    @Override
    protected SetProtocolResult doInBackground(SetProtocolParams... params) {

      SetProtocolResult result = new SetProtocolResult();

      try {

        result.activeProtocol = mReader.setProtocol(params[0].slotNum,
          params[0].preferredProtocols);

      } catch (Exception e) {

        result.e = e;
      }

      return result;
    }

    @Override
    protected void onPostExecute(SetProtocolResult result) {

      if (result.e != null) {

        logMsg(result.e.toString());

      } else {

        String activeProtocolString = "Active Protocol: ";

        switch (result.activeProtocol) {

          case Reader.PROTOCOL_T0:
            activeProtocolString += "T=0";
            break;

          case Reader.PROTOCOL_T1:
            activeProtocolString += "T=1";
            break;

          default:
            activeProtocolString += "Unknown";
            break;
        }

        // Show active protocol
        logMsg(activeProtocolString);
      }
    }
  }

  private class TransmitParams {

    public int slotNum;
    public int controlCode;
    public String commandString;
  }

  private class TransmitProgress {

    public int controlCode;
    public byte[] command;
    public int commandLength;
    public byte[] response;
    public int responseLength;
    public Exception e;
  }

  private class TransmitTask extends
    AsyncTask<TransmitParams, TransmitProgress, Void> {

    @Override
    protected Void doInBackground(TransmitParams... params) {

      TransmitProgress progress = null;

      byte[] command = null;
      byte[] response = null;
      int responseLength = 0;
      int foundIndex = 0;
      int startIndex = 0;

      do {

        // Find carriage return
        foundIndex = params[0].commandString.indexOf('\n', startIndex);
        if (foundIndex >= 0) {
          command = toByteArray(params[0].commandString.substring(
            startIndex, foundIndex));
        } else {
          command = toByteArray(params[0].commandString
            .substring(startIndex));
        }

        // Set next start index
        startIndex = foundIndex + 1;

        response = new byte[300];
        progress = new TransmitProgress();
        progress.controlCode = params[0].controlCode;
        try {

          if (params[0].controlCode < 0) {

            // Transmit APDU
            responseLength = mReader.transmit(params[0].slotNum,
              command, command.length, response,
              response.length);

          } else {

            // Transmit control command
            responseLength = mReader.control(params[0].slotNum,
              params[0].controlCode, command, command.length,
              response, response.length);
          }

          progress.command = command;
          progress.commandLength = command.length;
          progress.response = response;
          progress.responseLength = responseLength;
          progress.e = null;

        } catch (Exception e) {

          progress.command = null;
          progress.commandLength = 0;
          progress.response = null;
          progress.responseLength = 0;
          progress.e = e;
        }

        publishProgress(progress);

      } while (foundIndex >= 0);

      return null;
    }

    @Override
    protected void onProgressUpdate(TransmitProgress... progress) {

      if (progress[0].e != null) {

        logMsg(progress[0].e.toString());

      } else {

        logMsg("Command:");
        logBuffer(progress[0].command, progress[0].commandLength);

        logMsg("Response:");
        logBuffer(progress[0].response, progress[0].responseLength);

        if (progress[0].response != null
          && progress[0].responseLength > 0) {

          int controlCode;
          int i;
        }
      }
    }
  }

  /**
   * Called when the activity is first created.
   */
  @ReactMethod
  public void Init(Promise promise) {

    // Get USB manager
    mManager = (UsbManager) this.reactContext.getSystemService(Context.USB_SERVICE);

    // Initialize reader
    mReader = new Reader(mManager);
    mReader.setOnStateChangeListener(new OnStateChangeListener() {

      @Override
      public void onStateChange(int slotNum, int prevState, int currState) {

        if (prevState < Reader.CARD_UNKNOWN || prevState > Reader.CARD_SPECIFIC) {
          prevState = Reader.CARD_UNKNOWN;
        }

        if (currState < Reader.CARD_UNKNOWN || currState > Reader.CARD_SPECIFIC) {
          currState = Reader.CARD_UNKNOWN;
        }

        // Create output string
        final String outputString = "Slot " + slotNum + ": "
          + stateStrings[prevState] + " -> "
          + stateStrings[currState];

      }
    });

    // Register receiver for USB permission
    mPermissionIntent = PendingIntent.getBroadcast(this.reactContext, 0, new Intent(
      ACTION_USB_PERMISSION), 0);
    IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_USB_PERMISSION);
    filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
    this.reactContext.registerReceiver(mReceiver, filter);
  }

  @ReactMethod
  public void Disconnect() {
    // Close reader
    mReader.close();
    // Unregister receiver
    this.reactContext.unregisterReceiver(mReceiver);
  }

  /**
   * Logs the message.
   *
   * @param msg the message.
   */
  private void logMsg(String msg) {


  }

  /**
   * Logs the contents of buffer.
   *
   * @param buffer       the buffer.
   * @param bufferLength the buffer length.
   */
  private void logBuffer(byte[] buffer, int bufferLength) {

    String bufferString = "";

    for (int i = 0; i < bufferLength; i++) {

      String hexChar = Integer.toHexString(buffer[i] & 0xFF);
      if (hexChar.length() == 1) {
        hexChar = "0" + hexChar;
      }

      if (i % 16 == 0) {

        if (bufferString != "") {

          logMsg(bufferString);
          bufferString = "";
        }
      }

      bufferString += hexChar.toUpperCase() + " ";
    }

    if (bufferString != "") {
      logMsg(bufferString);
    }
  }

  /**
   * Converts the HEX string to byte array.
   *
   * @param hexString the HEX string.
   * @return the byte array.
   */
  private byte[] toByteArray(String hexString) {

    int hexStringLength = hexString.length();
    byte[] byteArray = null;
    int count = 0;
    char c;
    int i;

    // Count number of hex characters
    for (i = 0; i < hexStringLength; i++) {

      c = hexString.charAt(i);
      if (c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a'
        && c <= 'f') {
        count++;
      }
    }

    byteArray = new byte[(count + 1) / 2];
    boolean first = true;
    int len = 0;
    int value;
    for (i = 0; i < hexStringLength; i++) {

      c = hexString.charAt(i);
      if (c >= '0' && c <= '9') {
        value = c - '0';
      } else if (c >= 'A' && c <= 'F') {
        value = c - 'A' + 10;
      } else if (c >= 'a' && c <= 'f') {
        value = c - 'a' + 10;
      } else {
        value = -1;
      }

      if (value >= 0) {

        if (first) {

          byteArray[len] = (byte) (value << 4);

        } else {

          byteArray[len] |= value;
          len++;
        }

        first = !first;
      }
    }

    return byteArray;
  }

  /**
   * Converts the integer to HEX string.
   *
   * @param i the integer.
   * @return the HEX string.
   */
  private String toHexString(int i) {

    String hexString = Integer.toHexString(i);
    if (hexString.length() % 2 != 0) {
      hexString = "0" + hexString;
    }

    return hexString.toUpperCase();
  }

  /**
   * Converts the byte array to HEX string.
   *
   * @param buffer the buffer.
   * @return the HEX string.
   */
  private String toHexString(byte[] buffer) {

    String bufferString = "";

    for (int i = 0; i < buffer.length; i++) {

      String hexChar = Integer.toHexString(buffer[i] & 0xFF);
      if (hexChar.length() == 1) {
        hexChar = "0" + hexChar;
      }

      bufferString += hexChar.toUpperCase() + " ";
    }

    return bufferString;
  }
}
