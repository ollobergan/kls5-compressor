# Docker image for compression javascript files in KLS

## Author: **Ollobergan Karimov**

## Description
This image is used to compress js files in KLS.

- Java version: 17
- Used library: `yuicpmpressor.jar` and `compressjs.jar`

## How to use
1. Pull the image from docker hub
```bash
docker pull ollobergan/kls5-compressor:latest
```

2. Add Dockerfile in your project
```Dockerfile
FROM ollobergan/kls5-compressor:latest AS compressjs
RUN cd /app && \
    java -jar /app/compressjs.jar && \
    mv project.js "project$(cat /app/version.txt).js"
```

Note: `version.txt` file is used to add version to the compressed file. If you don't need version, you can remove it. If you need to add version, you can add it to the file or change it.
