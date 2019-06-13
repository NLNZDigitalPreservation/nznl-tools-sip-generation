package nz.govt.natlib.tools.sip.utils

import groovy.io.FileType
import groovy.util.logging.Log4j2
import nz.govt.natlib.tools.sip.files.FilesFinder
import nz.govt.natlib.tools.sip.logging.DefaultTimekeeper
import nz.govt.natlib.tools.sip.logging.Timekeeper
import nz.govt.natlib.tools.sip.state.SipProcessingException
import org.apache.commons.io.FilenameUtils

import java.nio.file.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Log4j2
class FileUtils {
    static final SimpleDateFormat FILE_TIMESTAMP_FORMATTER = new SimpleDateFormat('yyyy-MM-dd_HH-mm-ss-SSS')
    static final String MD5_HASH_ZERO_LENGTH_FILE = "d41d8cd98f00b204e9800998ecf8427e"
    static final String FILENAME_UNSAFE_CHARACTERS = ' *$'
    static final String REPLACEMENT_FILENAME_SAFE_CHARACTER = "-"
    static final String FILE_PATH_SEPARATORS = '/\\'
    static final String REPLACEMENT_FILE_PATH_SEPARATOR = "_"

    static String fileNameAsSafeString(String stringWithUnsafeCharacters) {
        String safeString = stringWithUnsafeCharacters
        FILENAME_UNSAFE_CHARACTERS.each { String unsafeCharacter ->
            safeString = safeString.replace(unsafeCharacter, REPLACEMENT_FILENAME_SAFE_CHARACTER)
        }
        FILE_PATH_SEPARATORS.each { String pathCharacter ->
            safeString = safeString.replace(pathCharacter, REPLACEMENT_FILE_PATH_SEPARATOR)
        }
        return safeString
    }

    static String filePathAsSafeString(File filePath, int totalSegments) {
        String fileSeparatorPattern = Pattern.quote(File.separator)
        String canonicalPath = filePath.getCanonicalPath()
        List<String> splitCanonicalPath = canonicalPath.split(fileSeparatorPattern)
        int pathLength = splitCanonicalPath.size()
        int numberSegments = totalSegments <= 0 ? pathLength : totalSegments
        int startingIndex = pathLength - numberSegments > 0 ? pathLength - numberSegments : 0
        StringBuilder unsafeBuilder = new StringBuilder()
        for (int pathIndex = startingIndex; pathIndex <=  pathLength - 1; pathIndex++) {
            unsafeBuilder.append(splitCanonicalPath.get(pathIndex))
            if (pathIndex < pathLength - 1) {
                unsafeBuilder.append(File.separator)
            }
        }
        return fileNameAsSafeString(unsafeBuilder.toString())
    }

    // Note that this method depends on the format of a given directory matching dates in the given range
    static List<File> allSubdirectoriesInDateRange(File rootDirectory, LocalDate startingDate, LocalDate endingDate,
                                                   DateTimeFormatter localDateFormatter = GeneralUtils.DATE_YYYYMMDD_FORMATTER,
                                                   boolean sort = true) {
        List<File> allSubdirectories = [ ]
        List<String> datesInRange = GeneralUtils.datesAsStringsInRange(startingDate, endingDate, localDateFormatter)
        rootDirectory.traverse(type: FileType.DIRECTORIES) { File directory ->
            String canonicalPath = directory.getCanonicalPath()
            boolean directoryMatches = datesInRange.any { String date ->
                canonicalPath.contains(date)
            }
            if (directoryMatches) {
                allSubdirectories.add(directory)
            }
        }
        // We could possibly sort by the date contained in the directory path, but we will assume some kind of ordering
        // that already exists in the directory structure. Otherwise we would create a map by date with a list of
        // directories and then go through each date and add the sorted list to the list of allSubdirectories.
        if (sort) {
            return allSubdirectories.sort() { File file1, File file2 ->
                file1.getCanonicalPath() <=> file2.getCanonicalPath()
            }
        } else {
            return allSubdirectories
        }
    }

    static List<File> allSubdirectories(File rootDirectory, boolean sort = true) {
        List<File> allSubdirectories = [ ]
        rootDirectory.traverse(type: FileType.DIRECTORIES) { File directory ->
            allSubdirectories.add(directory)
        }
        if (sort) {
            return allSubdirectories.sort() { File file1, File file2 ->
                file1.getCanonicalPath() <=> file2.getCanonicalPath()
            }
        } else {
            return allSubdirectories
        }
    }

    static List<File> findFiles(String localPath, boolean isRegexNotGlob, boolean matchFilenameOnly,
                                boolean sortFiles, String pattern, Timekeeper timekeeper = null,
                                boolean includeSubdirectories = true, boolean useDebug = false) {
        List<File> filesList = [ ]
        Path filesPath = Paths.get(localPath)
        if (!Files.exists(filesPath) || !Files.isDirectory(filesPath)) {
            log.warn("Path '${filesPath}' does not exist is not a directory. Returning empty file list.")
            return filesList
        }

        String message = "Finding files for path=${filesPath.toFile().getCanonicalPath()} and pattern=${pattern}"
        if (useDebug) {
            log.debug(message)
        } else {
            log.info(message)
        }
        if (timekeeper != null) {
            timekeeper.logElapsed(useDebug)
        }

        boolean directoryOnly = false
        filesList = FilesFinder.getMatchingFilesFull(filesPath, isRegexNotGlob, matchFilenameOnly, sortFiles,
                includeSubdirectories, directoryOnly, pattern)

        message = "Found total files=${GeneralUtils.TOTAL_FORMAT.format(filesList.size())} for path=${filesPath.toFile().getCanonicalPath()}"
        if (useDebug) {
            log.debug(message)
        } else {
            log.info(message)
        }
        if (timekeeper != null) {
            timekeeper.logElapsed(useDebug)
        }

        return filesList
    }

    static List<File> findNonMatchingFiles(String localPath, boolean isRegexNotGlob, boolean matchFilenameOnly,
                                           boolean sortFiles, String pattern, Timekeeper timekeeper) {
        List<File> filesList = [ ]
        Path filesPath = Paths.get(localPath)
        if (!Files.exists(filesPath) || !Files.isDirectory(filesPath)) {
            log.warn("Path '${filesPath}' does not exist is not a directory. Returning empty file list.")
            return filesList
        }

        log.info("Finding files for path=${filesPath.toFile().getCanonicalPath()} and pattern=${pattern}")
        timekeeper.logElapsed()
        filesList = FilesFinder.getNonMatchingFiles(filesPath, isRegexNotGlob, matchFilenameOnly, sortFiles, pattern)
        log.info("Found total files=${GeneralUtils.TOTAL_FORMAT.format(filesList.size())} for path=${filesPath.toFile().getCanonicalPath()}")
        timekeeper.logElapsed()

        return filesList
    }

    static List<File> matchFiles(List<File> allFiles, String pattern) {
        List<File> matchedFiles = [ ]
        allFiles.each { File file ->
            if (file.getName() ==~ /${pattern}/) {
                matchedFiles.add(file)
            }
        }

        return matchedFiles
    }

    static void copyOrMoveFiles(boolean moveFiles, List<File> sourceFiles, File destination) {
        sourceFiles.each { File sourceFile ->
            File destinationFile = new File(destination, sourceFile.getName())
            atomicMoveOrCopy(moveFiles, sourceFile, destinationFile)
        }
    }

    static boolean atomicMoveOrCopy(boolean moveFile, File sourceFile, File targetFile,
                                    boolean useAtomicOption = true, boolean includeDetailedTimings = false,
                                    Timekeeper atomicTimekeeper = null) {
        // Handle the case of being interrupted by copying/moving to the destination file (which leads to a bunch
        // partial copies -- especially in a multithreaded case -- that need to be manually checked to verify that
        // they are incomplete versions).
        // Instead, copy/move the file to a temporary-named file and then rename the file when the copy is complete.
        //
        // We only do a 'move' if we can do an atomic move, otherwise we do the following:
        // 1. Copy the file across with a '.tmpcopy' extension.
        // 2. Rename the file to the targetFile name.
        // 3. Delete the sourceFile.
        // This guarantees that we never delete the source file until the file has been copied and renamed.
        if (sourceFile.exists() && targetFile.exists() && Files.isSameFile(sourceFile.toPath(), targetFile.toPath())) {
            log.warn("atomicMoveOrCopy: NO move/copy -- source and target are the same PHYSICAL file!")
            log.warn("    sourceFile=${sourceFile.getCanonicalPath()}")
            log.warn("    targetFile=${targetFile.getCanonicalPath()}")
            return false
        }
        Timekeeper theTimekeeper = atomicTimekeeper
        if (includeDetailedTimings && atomicTimekeeper == null) {
            theTimekeeper = new DefaultTimekeeper()
            theTimekeeper.start()
        }
        boolean deleteSourceFile = moveFile && !useAtomicOption // because atomic move will automatically delete sourceFile
        boolean doCopy = !moveFile
        boolean renameSuccessful = false
        File temporaryDestinationFile = nonDuplicateFile(targetFile, true, "-",
                true, ".tmpcopy")
        if (includeDetailedTimings) {
            GeneralUtils.markElapsed(theTimekeeper, "sourceFile=${sourceFile.getName()}",
                    "Establish non-duplicate file for sourceFile path=" + sourceFile.getCanonicalPath())
        }
        if (moveFile) {
            // The only valid move option is StandardCopyOption.REPLACE_EXISTING, which we don't want to do
            if (useAtomicOption) {
                try {
                    Path resultingPath = Files.move(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    GeneralUtils.markElapsed(theTimekeeper, "sourceFile=${sourceFile.getName()}", "Atomic move completed")
                    renameSuccessful = true
                } catch (AtomicMoveNotSupportedException e) {
                    log.debug("Attempt at atomic file move file sourceFile=${sourceFile.getCanonicalPath()} to " +
                            "targetFile=${targetFile.getCanonicalPath()} failed, trying a non-atomic move approach.")
                    renameSuccessful = atomicMoveOrCopy(moveFile, sourceFile, targetFile, false,
                                                        false, theTimekeeper)
                    GeneralUtils.markElapsed(theTimekeeper, "sourceFile=${sourceFile.getName()}",
                            "Non-atomic move completed")
                }
            } else {
                doCopy = true
            }
        }
        if (doCopy) {
            Files.copy(sourceFile.toPath(), temporaryDestinationFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
            GeneralUtils.markElapsed(theTimekeeper, "sourceFile=${sourceFile.getName()}", "Copy completed")
            renameSuccessful = temporaryDestinationFile.renameTo(targetFile)
            GeneralUtils.markElapsed(theTimekeeper, "sourceFile=${sourceFile.getName()}", "Rename completed")
            if (renameSuccessful) {
                if (deleteSourceFile) {
                    Files.delete(sourceFile.toPath())
                    GeneralUtils.markElapsed(theTimekeeper, "sourceFile=${sourceFile.getName()}", "Delete completed")
                }
            } else {
                GeneralUtils.printAndFlush("\n")
                log.error("Unable to rename temporaryDestinationFile=${temporaryDestinationFile.getCanonicalPath()} " +
                        "to destinationFile=${targetFile.getCanonicalPath()}")
                if (deleteSourceFile) {
                    log.error("Not deleting sourceFile=${sourceFile}, as rename was NOT successful")
                }
            }
        }

        if (includeDetailedTimings && theTimekeeper != null) {
            GeneralUtils.markElapsed(theTimekeeper, "sourceFile=${sourceFile.getName()}", "Operation completed.")
            theTimekeeper.listMarkers()
        }
        return renameSuccessful
    }

    // Hash the files to determine if they are the same file.
    static boolean isSameFile(File file1, File file2, allowZeroLengthFiles = false) {
        // Skip MD5 hash if the files are the same physical file. Note that this will skip the zero-length file check.
        if (Files.isSameFile(file1.toPath(), file2.toPath())) {
            return true
        }
        String file1Md5Hash = generateMD5(file1)
        String file2Md5Hash = generateMD5(file2)

        if (!allowZeroLengthFiles) {
            boolean hasZeroLengthHashes = false
            String message = ""
            if (MD5_HASH_ZERO_LENGTH_FILE.equals(file1Md5Hash)) {
                hasZeroLengthHashes = true
                message += "file=${file1.getCanonicalPath()} has a prohibited zero-length file MD5 hash=${file1Md5Hash}"
            }
            if (MD5_HASH_ZERO_LENGTH_FILE.equals(file2Md5Hash)) {
                hasZeroLengthHashes = true
                if (message.length() > 0) {
                    message += ", "
                }
                message += "file=${file2.getCanonicalPath()} has a prohibited zero-length file MD5 hash=${file2Md5Hash}"
            }
            if (hasZeroLengthHashes) {
                throw new SipProcessingException(message)
            }
        }
        return file1Md5Hash.equals(file2Md5Hash)
    }

    static String generateMD5(File file) {
        MessageDigest digest = MessageDigest.getInstance("MD5")
        file.eachByte(4096) { byte[] buffer, int length ->
            digest.update(buffer, 0, length)
        }
        return digest.digest().encodeHex() as String
    }

    static File nonDuplicateFile(File originalFile, boolean usePreIndex = true, String preIndexString = "-DUPLICATE-",
                                 boolean useAdditionalExtension = false, String additionalExtension = ".tmp") {
        String fileName = originalFile.name
        String baseName = FilenameUtils.getBaseName(fileName)
        String extension = FilenameUtils.getExtension(fileName)
        File parentFile = originalFile.parentFile
        File candidateFile = null
        boolean alreadyExists = true
        int duplicateIndexCount = 0
        while (alreadyExists) {
            String preDuplicateIndexString = usePreIndex ? preIndexString : ""
            String extraExtension = useAdditionalExtension ? additionalExtension : ""
            String candidateFileName = baseName + preDuplicateIndexString + duplicateIndexCount + "." +
                    extension + extraExtension
            candidateFile = new File(candidateFileName, parentFile)
            alreadyExists = candidateFile.exists()
            duplicateIndexCount += 1
        }
        return candidateFile
    }
}