import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.services.CommonGoogleClientRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.clouddevices.CloudDevices;
import com.google.api.services.clouddevices.model.CloudDeviceChannel;
import com.google.api.services.clouddevices.model.Command;
import com.google.api.services.clouddevices.model.Device;
import com.google.api.services.clouddevices.model.DevicesListResponse;
import com.google.api.services.clouddevices.model.RegistrationTicket;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CloudDevicesSample {

  // See https://developers.google.com/weave/v1/dev-guides/getting-started/authorizing#setup
  // on how to set up your project and obtain client ID, client secret and API key.
  private static final String CLIENT_ID = "309435708548-fsvfu060n29531ufr5qqqgf7t2jhhvan.apps.googleusercontent.com";
  private static final String CLIENT_SECRET = "oFWicANTZPjRLdyC-gcJZdw-";
  private static final String API_KEY = "AIzaSyDaR-PSdRK0psv1ZyDcaY2n2UFpZWui2UE";
  private static final String AUTH_SCOPE = "https://www.googleapis.com/auth/weave.app";

  // Redirect URL for client side installed apps.
  private static final String REDIRECT_URL = "urn:ietf:wg:oauth:2.0:oob";

  private static final File CREDENTIALS_CACHE_FILE = new File("credentials_cache.json");

  // Command definitions of a new device if we need to create it.
  private static final String COMMAND_DEFS = "{" +
      "    \"storage\": {" +
      "     \"list\": {" +
      "       \"parameters\": {" +
      "        \"path\": {" +
      "          \"type\": \"string\"," +
      "          \"isRequired\": true" +
      "        }," +
      "        \"continuationToken\": {" +
      "          \"type\": \"string\"" +
      "        }," +
      "        \"entryCount\": {" +
      "          \"type\": \"integer\"" +
      "        }" +
      "       }" +
      "      }" +
      "     }," +
      "     \"_blinkLed\": {" +
      "     }" +
      "    }";

  public static void main(String[] args) {
    new CloudDevicesSample().run();
  }

  private final NetHttpTransport httpTransport = new NetHttpTransport();
  private final JacksonFactory jsonFactory = new JacksonFactory();

  public void run() {
    CloudDevices apiClient;
    try {
      apiClient = getApiClient();
    } catch (IOException e) { throw new RuntimeException("Could not get API client", e); }

    DevicesListResponse devicesListResponse;
    try {
      // Listing devices, request to devices.list API method, returns a list of devices
      // available to user. More details about the method:
      // https://developers.google.com/weave/v1/reference/cloud-api/devices/list
      devicesListResponse = apiClient.devices().list().execute();
    } catch (IOException e) { throw new RuntimeException("Could not list devices", e); }
    List<Device> devices = devicesListResponse.getDevices();
    Device device;
    if (devices == null || devices.isEmpty()) {
      System.out.println("No devices, creating one.");
      try {
        device = createDevice(apiClient);
        System.out.println("Created new device: " + device.getId());
      } catch (IOException e) {
        throw new RuntimeException("Could not create new device", e);
      }
    } else {
      //device = devices.get(0);
      for(Device dev : devices){
        System.out.println("Available device: " + dev.getId());
        try {
          // More about commands and command definitions:
          // https://developers.google.com/weave/v1/dev-guides/getting-started/commands-intro
          System.out.println(
              "Command definitions:\n" + jsonFactory.toPrettyString(dev.getCommandDefs()));
        }  catch (IOException e) { throw new RuntimeException(e); }
      }
    }
     
    /*System.out.println("Sending a new command to the device");
    Map<String, Object> parameters = new HashMap<String, Object>();
    parameters.put("path", "/tmp");
    Command command = new Command()
        .setName("storage.list")  // Command name to execute.
        .setParameters(parameters)  // Required command parameter.
        .setDeviceId(device.getId());  // Device to send the command to.
    // Calling commands.insert method to send command to the device, more details about the method:
    // https://developers.google.com/weave/v1/reference/cloud-api/commands/insert
    try {
      command = apiClient.commands().insert(command).execute();
    } catch (IOException e) { throw new RuntimeException("Could not insert command", e); }

    // The state of the command will be "queued". In normal situation a client may request
    // command again via commands.get API method to get command execution results, but our fake
    // device does not actually receive any commands, so it will never be executed.
    try {
      System.out.println("Sent command to the device:\n" + jsonFactory.toPrettyString(command));
    } catch (IOException e) { throw new RuntimeException(e); }*/
  }
   

  /**
   * Registers a new device making authenticated user the owner, check for more details:
   * https://developers.google.com/weave/v1/dev-guides/getting-started/register
   * @return the device just created
   */
  private Device createDevice(CloudDevices apiClient) throws IOException {
    GenericJson commandDefs =
        jsonFactory.createJsonParser(COMMAND_DEFS).parseAndClose(GenericJson.class);
    Device deviceDraft = new Device()
        .setDeviceKind("storage")
        .setSystemName("NAS 12418")
        .setDisplayName("Network Access Storage")
        .setChannel(new CloudDeviceChannel().setSupportedType("xmpp"))
        .set("commandDefs", commandDefs);
    RegistrationTicket ticket = apiClient.registrationTickets().insert(
        new RegistrationTicket()
            .setOauthClientId(CLIENT_ID)
            .setDeviceDraft(deviceDraft)
            .setUserEmail("me"))
        .execute();
    ticket = apiClient.registrationTickets().finalize(ticket.getId()).execute();
    return ticket.getDeviceDraft();
  }

  private CloudDevices getApiClient() throws IOException {
    // Try to load cached credentials.
    GoogleCredential credential = getCachedCredential();
    if (credential == null) {
      System.out.println("Did not find cached credentials");
      credential = authorize();
    }
    return new CloudDevices.Builder(httpTransport, jsonFactory, credential)
        .setApplicationName("Weave Sample")
        .setServicePath("clouddevices/v1")
        .setGoogleClientRequestInitializer(new CommonGoogleClientRequestInitializer(API_KEY))
        .build();
  }

  /**
   * Goes through Google OAuth2 authorization flow. See more details:
   * https://developers.google.com/weave/v1/dev-guides/getting-started/authorizing
   */
  private GoogleCredential authorize() throws IOException {
    // Generate the URL to send the user to grant access.
    // There are also other flows that may be used for authorization:
    // https://developers.google.com/accounts/docs/OAuth2
    String authorizationUrl = new GoogleAuthorizationCodeRequestUrl(
        CLIENT_ID, REDIRECT_URL, Collections.singleton(AUTH_SCOPE)).build();
    // Direct user to the authorization URI.
    System.out.println("Go to the following link in your browser:");
    System.out.println(authorizationUrl);
    // Get authorization code from user.
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    System.out.println("What is the authorization code?");
    String authorizationCode = in.readLine();

    // Use the authorization code to get an access token and a refresh token.
    GoogleTokenResponse response = new GoogleAuthorizationCodeTokenRequest(
        httpTransport, jsonFactory, CLIENT_ID, CLIENT_SECRET, authorizationCode,
        REDIRECT_URL).execute();
    cacheCredential(response.getRefreshToken());
    // Use the access and refresh tokens to set up credentials.
    GoogleCredential credential = new GoogleCredential.Builder()
        .setJsonFactory(jsonFactory)
        .setTransport(httpTransport)
        .setClientSecrets(CLIENT_ID, CLIENT_SECRET)
        .build()
        .setFromTokenResponse(response);
    return credential;
  }

  private GoogleCredential getCachedCredential() {
    try {
      return GoogleCredential.fromStream(new FileInputStream(CREDENTIALS_CACHE_FILE));
    } catch (IOException e) {
      return null;
    }
  }

  private void cacheCredential(String refreshToken) {
    GenericJson json = new GenericJson();
    json.setFactory(jsonFactory);
    json.put("client_id", CLIENT_ID);
    json.put("client_secret", CLIENT_SECRET);
    json.put("refresh_token", refreshToken);
    json.put("type", "authorized_user");
    FileOutputStream out = null;
    try {
      out = new FileOutputStream(CREDENTIALS_CACHE_FILE);
      out.write(json.toPrettyString().getBytes(Charset.defaultCharset()));
    } catch (IOException e) {
      System.err.println("Error caching credentials");
      e.printStackTrace();
    } finally {
      if (out != null) {
        try { out.close(); } catch (IOException e) { /* Ignore. */ }
      }
    }
  }
}
