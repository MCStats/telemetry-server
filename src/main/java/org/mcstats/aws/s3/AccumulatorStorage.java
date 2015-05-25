package org.mcstats.aws.s3;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;
import com.amazonaws.util.StringUtils;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Storage for accumulator on S3
 */
public class AccumulatorStorage {

    private static final String PLUGIN_DATA_KEY = "plugins/%d.json.gz"; // bucket

    private static final Logger logger = Logger.getLogger(AccumulatorStorage.class);

    private final Gson gson;
    private final AmazonS3 s3;
    private final String bucket;

    @Inject
    public AccumulatorStorage(Gson gson,
                              @Named("accumulator.s3-bucket") String bucket,
                              @Named("aws.access-key") String accessKey,
                              @Named("aws.secret-key") String secretKey) {
        this.gson = gson;
        this.bucket = bucket;
        s3 = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey));
    }

    /**
     * Puts accumulated plugin data into S3.
     *
     * @param bucket
     * @param data
     */
    public void putPluginData(int bucket, Map<Integer, Map<String, Map<String, Long>>> data) {
        final String key = String.format(PLUGIN_DATA_KEY, bucket);

        final ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentEncoding("gzip");
        metadata.setContentType("application/json");

        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();

        try {
            GZIPOutputStream out = new GZIPOutputStream(byteOutputStream);
            out.write(gson.toJson(data).getBytes(StringUtils.UTF8));
            out.finish();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        InputStream dataInputStream = new ByteArrayInputStream(byteOutputStream.toByteArray());

        logger.info("Putting object to s3://" + this.bucket + "/" + key);

        s3.putObject(new PutObjectRequest(this.bucket, key, dataInputStream, metadata));
    }

    /**
     * Gets accumulated plugin data from S3.
     *
     * @param bucket
     * @return
     */
    public Map<Integer, Map<String, Map<String, Long>>> getPluginData(int bucket) {
        final String key = String.format(PLUGIN_DATA_KEY, bucket);

        logger.info("Getting object from s3://" + this.bucket + "/" + key);

        final S3Object object;

        try {
            object = s3.getObject(new GetObjectRequest(this.bucket, key));
        } catch (AmazonS3Exception e) {
            return null;
        }

        if (object == null) {
            return null;
        }

        try {
            String data = IOUtils.toString(new GZIPInputStream(object.getObjectContent()));

            Type type = new TypeToken<Map<Integer, Map<String, Map<String, Long>>>>(){}.getType();
            return gson.fromJson(data, type);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}
