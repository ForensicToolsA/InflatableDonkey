/*
 * The MIT License
 *
 * Copyright 2016 Ahseya.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.horrorho.inflatabledonkey.cloud.accounts;

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;
import com.github.horrorho.inflatabledonkey.cloud.auth.Auth;
import com.github.horrorho.inflatabledonkey.requests.AccountSettingsRequestFactory;
import com.github.horrorho.inflatabledonkey.responsehandler.PropertyListResponseHandler;
import com.github.horrorho.inflatabledonkey.util.PListsLegacy;
import java.io.IOException;
import java.text.ParseException;
import javax.xml.parsers.ParserConfigurationException;
import javax.annotation.concurrent.Immutable;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * AccountInfos.
 *
 * @author Ahseya
 */
@Immutable
public final class Accounts {

    private static final Logger logger = LoggerFactory.getLogger(Accounts.class);

    private Accounts() {
    }

    public static Account account(HttpClient httpClient, Auth auth) throws IOException {
        HttpUriRequest accountSettingsRequest = AccountSettingsRequestFactory.instance()
                .apply(auth.dsPrsID(), auth.mmeAuthToken());

        accountSettingsRequest.setHeader("X-Mme-Client-Info", "<iPhone10,2> <iPhone OS;11.1.1;15G77)> <com.apple.cloudkit.CloudKitDaemon/482.30 (com.apple.cloudd/482.30)>");
        NSDictionary settings
                = httpClient.execute(accountSettingsRequest, PropertyListResponseHandler.dictionary());

        return account(settings);
    }

    public static Account account(byte[] bs) {
        try {
            NSDictionary dict = (NSDictionary) PropertyListParser.parse(bs);
            return account(dict);
        } catch (IOException | PropertyListFormatException | ParseException | ParserConfigurationException | SAXException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    public static Account account(NSDictionary settings) {
        logger.debug("-- account() - settings: {}", settings.toASCIIPropertyList());

        NSDictionary accountInfoDict = PListsLegacy.getAs(settings, "appleAccountInfo", NSDictionary.class);
        NSDictionary tokensDict = PListsLegacy.getAs(settings, "tokens", NSDictionary.class);
        NSDictionary mobileMeDict = PListsLegacy.getAs(settings, "com.apple.mobileme", NSDictionary.class);

        AccountInfo accountInfo = new AccountInfo(accountInfoDict);
        Tokens tokens = new Tokens(tokensDict);
        MobileMe mobileMe = new MobileMe(mobileMeDict);

        return new Account(mobileMe, accountInfo, tokens);
    }
}
