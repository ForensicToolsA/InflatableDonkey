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
package com.github.horrorho.inflatabledonkey.data.backup;

import com.github.horrorho.inflatabledonkey.pcs.zone.ProtectionZone;
import com.github.horrorho.inflatabledonkey.protobuf.CloudKit;
import com.google.protobuf.ByteString;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.annotation.concurrent.Immutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Ahseya
 */
@Immutable
public final class AssetFactory {

    private static final Logger logger = LoggerFactory.getLogger(AssetFactory.class);

    private static final String CONTENTS = "contents";
    private static final String ENCRYPTED_ATTRIBUTES = "encryptedAttributes";
    private static final String FILE_TYPE = "fileType";
    private static final String PROTECTION_CLASS = "protectionClass";

    public static Optional<Asset> from(CloudKit.Record record, String domain, ProtectionZone zone) {
        return AssetID.from(record.getRecordIdentifier().getValue().getName())
                .map(u -> from(u, domain, record, zone));
    }

    static Asset from(AssetID assetID, String domain, CloudKit.Record record, ProtectionZone zone) {
        List<CloudKit.Record.Field> records = record.getRecordFieldList();
        Optional<Integer> protectionClass = protectionClass(records);
        Optional<Integer> fileType = fileType(records);
        Optional<AssetEncryptedAttributes> encryptedAttributes = encryptedAttributes(records)
                .flatMap(u -> zone.decrypt(u, ENCRYPTED_ATTRIBUTES))
                .flatMap(u -> AssetEncryptedAttributesFactory.from(u, domain));
        Optional<CloudKit.Asset> asset = asset(records);
        Optional<byte[]> keyEncryptionKey = asset.filter(CloudKit.Asset::hasProtectionInfo)

                .map(u -> u.getProtectionInfo().getProtectionInfo().toByteArray())
                .flatMap(zone::unwrapKey);
        Optional<byte[]> fileChecksum = asset.filter(CloudKit.Asset::hasSignature)
                .map(u -> u.getSignature().toByteArray());
        Optional<byte[]> fileSignature = asset.filter(CloudKit.Asset::hasReferenceSignature)
                .map(u -> u.getReferenceSignature().toByteArray());
        Optional<String> contentBaseURL = asset.filter(CloudKit.Asset::hasContentBaseURL)
                .map(CloudKit.Asset::getContentBaseURL);
        Optional<String> dsPrsID = asset.filter(CloudKit.Asset::hasOwner)
                .map(CloudKit.Asset::getOwner);
        Optional<Long> fileSize = asset.filter(CloudKit.Asset::hasSize)
                .map(CloudKit.Asset::getSize);
        Optional<Instant> downloadTokenExpiration = asset.filter(CloudKit.Asset::hasDownloadTokenExpiration)
                .map(CloudKit.Asset::getDownloadTokenExpiration)
                .map(Instant::ofEpochSecond);

        Asset newAsset = new Asset(
                record,
                assetID,
                protectionClass,
                fileSize,
                fileType,
                downloadTokenExpiration,
                dsPrsID,
                contentBaseURL,
                fileChecksum,
                fileSignature,
                keyEncryptionKey,
                encryptedAttributes,
                asset);
        logger.debug("-- from() - asset: {}", newAsset);
        return newAsset;
    }

    static Optional<Integer> protectionClass(List<CloudKit.Record.Field> records) {
        return records.stream()
                .filter(u -> u
                .getIdentifier()
                .getName()
                .equals(PROTECTION_CLASS))
                .map(u -> u
                .getValue()
                .getSignedValue())
                .map(Long::intValue)
                .findFirst();
    }

    static Optional<Integer> fileType(List<CloudKit.Record.Field> records) {
        return records.stream()
                .filter(value -> value.getIdentifier().getName().equals(FILE_TYPE))
                .map(u -> u
                .getValue()
                .getSignedValue())
                .map(Long::intValue)
                .findFirst();
    }

    static Optional<CloudKit.Asset> asset(List<CloudKit.Record.Field> records) {
        return records.stream()
                .filter(value -> value
                .getIdentifier()
                .getName()
                .equals(CONTENTS))
                .map(u -> u
                .getValue()
                .getAssetValue())
                .findFirst();
    }

    static Optional<byte[]> encryptedAttributes(List<CloudKit.Record.Field> records) {
        return records.stream()
                .filter(u -> u
                .getIdentifier()
                .getName()
                .equals(ENCRYPTED_ATTRIBUTES))
                .map(u -> u
                .getValue()
                .getBytesValue())
                .map(ByteString::toByteArray)
                .findFirst();
    }
}
