/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
//package sms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.apache.commons.codec.binary.Base64;
public class Sms {
    public static void Text(String message) {
        try {
            String phoneNumber = "3198559300";//3198559300
            String appKey = "fbc84828-8884-4815-83d5-1529a937d086";
            String appSecret = "EA0LjuptWkCkArjSupE/0A==";
            URL url = new URL("https://messagingapi.sinch.com/v1/sms/" + phoneNumber);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            String userCredentials = "application\\" + appKey + ":" + appSecret;
            byte[] encoded = Base64.encodeBase64(userCredentials.getBytes());
            String basicAuth = "Basic " + new String(encoded);
            connection.setRequestProperty("Authorization", basicAuth);
            String postData = "{\"Message\":\"" + message + "\"}";
            OutputStream os = connection.getOutputStream();
            os.write(postData.getBytes());
            StringBuilder response = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            while ( (line = br.readLine()) != null)
                response.append(line);
            br.close();
            os.close();
            System.out.println(response.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
