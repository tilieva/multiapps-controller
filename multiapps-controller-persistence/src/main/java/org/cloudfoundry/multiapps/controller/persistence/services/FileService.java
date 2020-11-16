package org.cloudfoundry.multiapps.controller.persistence.services;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.cloudfoundry.multiapps.common.SLException;
import org.cloudfoundry.multiapps.common.util.DigestHelper;
import org.cloudfoundry.multiapps.controller.persistence.DataSourceWithDialect;
import org.cloudfoundry.multiapps.controller.persistence.Messages;
import org.cloudfoundry.multiapps.controller.persistence.model.FileEntry;
import org.cloudfoundry.multiapps.controller.persistence.model.FileInfo;
import org.cloudfoundry.multiapps.controller.persistence.model.ImmutableFileEntry;
import org.cloudfoundry.multiapps.controller.persistence.model.ImmutableFileInfo;
import org.cloudfoundry.multiapps.controller.persistence.query.providers.ExternalSqlFileQueryProvider;
import org.cloudfoundry.multiapps.controller.persistence.query.providers.SqlFileQueryProvider;
import org.cloudfoundry.multiapps.controller.persistence.util.SqlQueryExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileService {

    protected static final String DEFAULT_TABLE_NAME = "LM_SL_PERSISTENCE_FILE";

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final FileStorage fileStorage;
    private final SqlQueryExecutor sqlQueryExecutor;
    private final SqlFileQueryProvider sqlFileQueryProvider;

    public FileService(DataSourceWithDialect dataSourceWithDialect, FileStorage fileStorage) {
        this(DEFAULT_TABLE_NAME, dataSourceWithDialect, fileStorage);
    }

    public FileService(String tableName, DataSourceWithDialect dataSourceWithDialect, FileStorage fileStorage) {
        this(dataSourceWithDialect, new ExternalSqlFileQueryProvider(tableName, dataSourceWithDialect.getDataSourceDialect()), fileStorage);
    }

    protected FileService(DataSourceWithDialect dataSourceWithDialect, SqlFileQueryProvider sqlFileQueryProvider, FileStorage fileStorage) {
        this.sqlQueryExecutor = new SqlQueryExecutor(dataSourceWithDialect.getDataSource());
        this.sqlFileQueryProvider = sqlFileQueryProvider.withLogger(logger);
        this.fileStorage = fileStorage;
    }

    /**
     * Uploads a new file.
     *
     * @param space the uploaded file will be associated with the specified space
     * @param namespace namespace where the file will be uploaded
     * @param name name of the uploaded file
     * @param inputStream input stream to read the content from
     * @return an object representing the file upload
     * @throws FileStorageException if the file cannot be uploaded
     */
    public FileEntry addFile(String space, String namespace, String name, InputStream inputStream) throws FileStorageException {
        // Stream the file to a temp location and get the size and MD5 digest
        // as an alternative we can pass the original stream to the database,
        // and decorate the blob stream to calculate digest and size, but this will still require
        // two roundtrips to the database (insert of the content and then update with the digest and
        // size), which is probably inefficient
        FileInfo fileInfo = null;
        FileEntry fileEntry = null;
        try (InputStream autoClosedInputStream = inputStream) {
            fileInfo = FileUploader.uploadFile(inputStream);
            fileEntry = addFile(space, namespace, name, fileInfo);
        } catch (IOException e) {
            logger.debug(e.getMessage(), e);
        } finally {
            if (fileInfo != null) {
                FileUploader.removeFile(fileInfo);
            }
        }
        return fileEntry;
    }

    public FileEntry addFile(String space, String namespace, String name, File existingFile) throws FileStorageException {
        try {
            FileInfo fileInfo = createFileInfo(existingFile);

            return addFile(space, namespace, name, fileInfo);
        } catch (NoSuchAlgorithmException e) {
            throw new SLException(Messages.ERROR_CALCULATING_FILE_DIGEST, existingFile.getName(), e);
        } catch (FileNotFoundException e) {
            throw new FileStorageException(MessageFormat.format(Messages.ERROR_FINDING_FILE_TO_UPLOAD, existingFile.getName()), e);
        } catch (IOException e) {
            throw new FileStorageException(MessageFormat.format(Messages.ERROR_READING_FILE_CONTENT, existingFile.getName()), e);
        }
    }

    public List<FileEntry> listFiles(String space, String namespace) throws FileStorageException {
        try {
            return getSqlQueryExecutor().execute(getSqlFileQueryProvider().getListFilesQuery(space, namespace));
        } catch (SQLException e) {
            throw new FileStorageException(MessageFormat.format(Messages.ERROR_GETTING_FILES_WITH_SPACE_AND_NAMESPACE, space, namespace),
                                           e);
        }
    }

    public FileEntry getFile(String space, String id) throws FileStorageException {
        try {
            return getSqlQueryExecutor().execute(getSqlFileQueryProvider().getRetrieveFileQuery(space, id));
        } catch (SQLException e) {
            throw new FileStorageException(e.getMessage(), e);
        }
    }

    public void consumeFileContent(String space, String id, FileContentConsumer fileContentConsumer) throws FileStorageException {
        processFileContent(space, id, inputStream -> {
            fileContentConsumer.consume(inputStream);
            return null;
        });
    }

    public <T> T processFileContent(String space, String id, FileContentProcessor<T> fileContentProcessor) throws FileStorageException {
        return fileStorage.processFileContent(space, id, fileContentProcessor);
    }

    public int deleteBySpaceAndNamespace(String space, String namespace) throws FileStorageException {
        fileStorage.deleteFilesBySpaceAndNamespace(space, namespace);
        return deleteFileAttributesBySpaceAndNamespace(space, namespace);
    }

    public int deleteBySpaces(List<String> spaces) throws FileStorageException {
        fileStorage.deleteFilesBySpaces(spaces);
        return deleteFileAttributesBySpaces(spaces);
    }

    public int deleteModifiedBefore(Date modificationTime) throws FileStorageException {
        int deletedItems = fileStorage.deleteFilesModifiedBefore(modificationTime);
        return deleteFileAttributesModifiedBefore(modificationTime) + deletedItems;
    }

    public boolean deleteFile(String space, String id) throws FileStorageException {
        fileStorage.deleteFile(id, space);
        return deleteFileAttribute(space, id);
    }

    public int deleteFilesEntriesWithoutContent() throws FileStorageException {
        try {
            List<FileEntry> entries = getSqlQueryExecutor().execute(getSqlFileQueryProvider().getListAllFilesQuery());
            List<FileEntry> missing = fileStorage.getFileEntriesWithoutContent(entries);
            return deleteFileEntries(missing);
        } catch (SQLException e) {
            throw new FileStorageException(Messages.ERROR_GETTING_ALL_FILES, e);
        }
    }

    protected void storeFile(FileEntry fileEntry, FileInfo fileInfo) throws FileStorageException {
        fileStorage.addFile(fileEntry, fileInfo.getFile());
        storeFileAttributes(fileEntry);
    }

    protected boolean deleteFileAttribute(String space, String id) throws FileStorageException {
        try {
            return getSqlQueryExecutor().execute(getSqlFileQueryProvider().getDeleteFileEntryQuery(space, id));
        } catch (SQLException e) {
            throw new FileStorageException(e.getMessage(), e);
        }
    }

    protected int deleteFileAttributesModifiedBefore(Date modificationTime) throws FileStorageException {
        try {
            return getSqlQueryExecutor().execute(getSqlFileQueryProvider().getDeleteModifiedBeforeQuery(modificationTime));
        } catch (SQLException e) {
            throw new FileStorageException(e.getMessage(), e);
        }
    }

    protected int deleteFileAttributesBySpaceAndNamespace(String space, String namespace) throws FileStorageException {
        try {
            return getSqlQueryExecutor().execute(getSqlFileQueryProvider().getDeleteBySpaceAndNamespaceQuery(space, namespace));
        } catch (SQLException e) {
            throw new FileStorageException(e.getMessage(), e);
        }
    }

    protected int deleteFileAttributesBySpaces(List<String> spaces) throws FileStorageException {
        try {
            return getSqlQueryExecutor().execute(getSqlFileQueryProvider().getDeleteBySpacesQuery(spaces));
        } catch (SQLException e) {
            throw new FileStorageException(e.getMessage(), e);
        }
    }

    protected FileEntry createFileEntry(String space, String namespace, String name, FileInfo localFile) {
        return ImmutableFileEntry.builder()
                                 .id(generateRandomId())
                                 .space(space)
                                 .name(name)
                                 .namespace(namespace)
                                 .size(localFile.getSize())
                                 .digest(localFile.getDigest())
                                 .digestAlgorithm(localFile.getDigestAlgorithm())
                                 .modified(new Timestamp(System.currentTimeMillis()))
                                 .build();
    }

    protected SqlQueryExecutor getSqlQueryExecutor() {
        return sqlQueryExecutor;
    }

    protected SqlFileQueryProvider getSqlFileQueryProvider() {
        return sqlFileQueryProvider;
    }

    private FileInfo createFileInfo(File existingFile) throws NoSuchAlgorithmException, IOException {
        return ImmutableFileInfo.builder()
                                .file(existingFile)
                                .size(BigInteger.valueOf(existingFile.length()))
                                .digest(DigestHelper.computeFileChecksum(existingFile.toPath(), FileUploader.DIGEST_METHOD))
                                .digestAlgorithm(FileUploader.DIGEST_METHOD)
                                .build();
    }

    private FileEntry addFile(String space, String namespace, String name, FileInfo fileInfo) throws FileStorageException {

        FileEntry fileEntry = createFileEntry(space, namespace, name, fileInfo);
        storeFile(fileEntry, fileInfo);
        logger.debug(MessageFormat.format(Messages.STORED_FILE_0, fileEntry));
        return fileEntry;
    }

    private String generateRandomId() {
        return UUID.randomUUID()
                   .toString();
    }

    private boolean storeFileAttributes(FileEntry fileEntry) throws FileStorageException {
        try {
            return getSqlQueryExecutor().execute(getSqlFileQueryProvider().getStoreFileAttributesQuery(fileEntry));
        } catch (SQLException e) {
            throw new FileStorageException(e.getMessage(), e);
        }
    }

    private int deleteFileEntries(List<FileEntry> fileEntries) throws FileStorageException {
        try {
            return getSqlQueryExecutor().execute(getSqlFileQueryProvider().getDeleteFileEntriesQuery(fileEntries));
        } catch (SQLException e) {
            throw new FileStorageException(e.getMessage(), e);
        }
    }

}
