/*
 * Copyright 2011 Robert Theis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.jllarraz.com.passportreader.asynctask;

import android.content.Context;
import android.graphics.Bitmap;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.AsyncTask;
import android.util.Log;

import net.sf.scuba.smartcards.CardService;
import net.sf.scuba.smartcards.CardServiceException;

import org.jmrtd.BACKeySpec;
import org.jmrtd.PassportService;

import org.jmrtd.cert.CVCPrincipal;
import org.jmrtd.lds.CVCAFile;
import org.jmrtd.lds.ChipAuthenticationPublicKeyInfo;
import org.jmrtd.lds.LDSFileUtil;
import org.jmrtd.lds.icao.DG11File;
import org.jmrtd.lds.icao.DG14File;
import org.jmrtd.lds.icao.DG1File;
import org.jmrtd.lds.icao.DG2File;
import org.jmrtd.lds.icao.DG5File;
import org.jmrtd.lds.icao.MRZInfo;
import org.jmrtd.protocol.CAResult;


import java.io.FileInputStream;
import java.io.InputStream;

import java.math.BigInteger;
import java.security.KeyStore;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.net.ssl.TrustManagerFactory;

import example.jllarraz.com.passportreader.utils.ImageUtil;
import example.jllarraz.com.passportreader.utils.MRZUtil;
import example.jllarraz.com.passportreader.utils.PassportNfcUtils;

public final class NfcPassportAsyncTask extends AsyncTask<Void, Void, Boolean> {

    private static final String TAG = NfcPassportAsyncTask.class.getSimpleName();

    private Context context;
    private Tag tag;
    private MRZInfo mrzInfo;
    private NfcPassportAsyncTaskListener nfcPassportAsyncTaskListener;

    private CardServiceException cardServiceException;
    private MRZInfo mrzInfoResult;
    private Bitmap faceImage;
    private Bitmap portraitImage;


    public NfcPassportAsyncTask(Context context, Tag imageProcessor, MRZInfo mrzInfo, NfcPassportAsyncTaskListener nfcPassportAsyncTaskListener) {
        this.context = context;
        this.tag = imageProcessor;
        this.mrzInfo = mrzInfo;
        this.nfcPassportAsyncTaskListener = nfcPassportAsyncTaskListener;
    }

    @Override
    protected Boolean doInBackground(Void... arg0) {
        return handle(tag);
    }

    @Override
    protected void onPostExecute(Boolean result) {
    super.onPostExecute(result);
        if(result){
            onPassportRead(mrzInfoResult, faceImage);
        }
        else {
            onCardException(cardServiceException);
        }
    }


  protected boolean handle(Tag tag){
    PassportService ps = null;
    try {
      IsoDep nfc = IsoDep.get(tag);
      CardService cs = CardService.getInstance(nfc);
      ps = new PassportService(cs);
      ps.open();

      ps.sendSelectApplet(false);
      BACKeySpec bacKey = new BACKeySpec() {
        @Override
        public String getDocumentNumber() {
          return mrzInfo.getDocumentNumber();
        }

        @Override
        public String getDateOfBirth() {
          return mrzInfo.getDateOfBirth();
        }

        @Override
        public String getDateOfExpiry() {
          return mrzInfo.getDateOfExpiry();
        }
      };

      ps.doBAC(bacKey);

      InputStream is = null;
      InputStream isPicture = null;
      InputStream isPortrait = null;
      InputStream isAdditionalPersonalDetails = null;
      try {
        // Basic data
        is = ps.getInputStream(PassportService.EF_DG1);
        DG1File dg1 = (DG1File) LDSFileUtil.getLDSFile(PassportService.EF_DG1, is);

        //Picture
        isPicture = ps.getInputStream(PassportService.EF_DG2);
        DG2File dg2 = (DG2File)LDSFileUtil.getLDSFile(PassportService.EF_DG2, isPicture);

        //Get the picture
        Bitmap faceImage = null;
        try {
          faceImage = PassportNfcUtils.retrieveFaceImage(context, dg2);
        }catch (Exception e){
          //Don't do anything
          e.printStackTrace();
        }


        //Portrait
        //Get the picture
        Bitmap portraitImage = null;
        try {
            isPortrait = ps.getInputStream(PassportService.EF_DG5);
            DG5File dg5 = (DG5File)LDSFileUtil.getLDSFile(PassportService.EF_DG5, isPortrait);
            portraitImage = PassportNfcUtils.retrievePortraitImage(context, dg5);
        }catch (Exception e){
          //Don't do anything
            Log.e(TAG, "Portrait image: "+e);
        }

          /*try {
              isAdditionalPersonalDetails = ps.getInputStream(PassportService.EF_DG11);
              DG11File dg11 = (DG11File)LDSFileUtil.getLDSFile(PassportService.EF_DG11, isAdditionalPersonalDetails);
              String custodyInformation = dg11.getCustodyInformation();
              Date fullDateOfBirth = dg11.getFullDateOfBirth();
              String nameOfHolder = dg11.getNameOfHolder();
              List<String> otherNames = dg11.getOtherNames();
              List<String> otherValidTDNumbers = dg11.getOtherValidTDNumbers();
              List<String> permanentAddress = dg11.getPermanentAddress();
              String personalNumber = dg11.getPersonalNumber();
              String personalSummary = dg11.getPersonalSummary();
              List<String> placeOfBirth = dg11.getPlaceOfBirth();
              String profession = dg11.getProfession();
              byte[] proofOfCitizenship = dg11.getProofOfCitizenship();
              int tag1 = dg11.getTag();
              List<Integer> tagPresenceList = dg11.getTagPresenceList();
              String telephone = dg11.getTelephone();
              String title = dg11.getTitle();

              if(dg11!=null){

              }

          }catch (Exception e){
              //Don't do anything
              Log.e(TAG, "Additional Personal Details: "+e);
          }*/


          // Chip Authentication
          List<CAResult> caResults = PassportNfcUtils.doChipAuthentication(ps);
          if(caResults.size()>0){
              Log.d(TAG, "Chip authentication success ");
          }

          /*
          //EAC
          //First we load our keystore
          KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
          // get user password and file input stream
          char[] password = "MY_PASSWORD".toCharArray();//Keystore password
          try (FileInputStream fis = new FileInputStream("keyStoreName")) {
              ks.load(fis, password);
          }
          List<KeyStore> keyStoreList = new ArrayList<>();
          keyStoreList.add(ks);

          //WE try to do EAC with the certificates in our Keystore
          PassportNfcUtils.doEac(ps, dg1.getMRZInfo().getDocumentNumber(), keyStoreList);
            */

        mrzInfoResult = dg1.getMRZInfo();
        this.faceImage = faceImage;
        this.portraitImage = portraitImage;
        //TODO EAC
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        try {
            if(is!=null) {
                is.close();
            }
            if(isPicture!=null) {
                isPicture.close();
            }
            if(isPortrait!=null) {
              isPortrait.close();
            }

            if(isAdditionalPersonalDetails!=null) {
                isAdditionalPersonalDetails.close();
            }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    } catch (CardServiceException e) {
      cardServiceException = e;
      return false;
    } finally {
      try {
          if (ps != null){
              ps.close();
          }

      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
    return true;
  }

  protected void onCardException(CardServiceException cardException){
    if(nfcPassportAsyncTaskListener!=null){
        nfcPassportAsyncTaskListener.onCardException(cardException);
    }
  }

  protected void onPassportRead(final MRZInfo personInfo, final Bitmap faceImage){
    if(nfcPassportAsyncTaskListener!=null){
      nfcPassportAsyncTaskListener.onPassportRead(personInfo, faceImage);
    }
  }

  public interface NfcPassportAsyncTaskListener{
    void onPassportRead(MRZInfo personInfo, Bitmap faceImage);
    void onCardException(CardServiceException cardException);
  }
}