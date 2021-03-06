package nz.govt.natlib.tools.sip.utils

import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.filefilter.TrueFileFilter
import org.apache.commons.lang.SystemUtils
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

import static org.hamcrest.core.Is.is
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue

class PathUtilsTest {
    static final Path ZERO_SEGMENT_FILE = Path.of("")
    static final Path ZERO_SEGMENT_FILE_SLASH = Path.of(FilenameUtils.separatorsToSystem("/"))
    static final Path ONE_SEGMENT_FILE = Path.of(FilenameUtils.separatorsToSystem("filename.txt"))
    static final Path ONE_SEGMENT_FILE_SLASH = Path.of(FilenameUtils.separatorsToSystem("/filename.txt"))
    static final Path TWO_SEGMENT_FILE = Path.of(FilenameUtils.separatorsToSystem("parent1/filename.txt"))
    static final Path TWO_SEGMENT_FILE_SLASH = Path.of(FilenameUtils.separatorsToSystem("/parent1/filename.txt"))
    static final Path THREE_SEGMENT_FILE = Path.of(FilenameUtils.separatorsToSystem("parent2/parent1/filename.txt"))
    static final Path THREE_SEGMENT_FILE_SLASH = Path.of(FilenameUtils.separatorsToSystem("/parent2/parent1/filename.txt"))
    static final Path FOUR_SEGMENT_FILE = Path.of(FilenameUtils.separatorsToSystem("parent3/parent2/parent1/filename.txt"))
    static final Path FOUR_SEGMENT_FILE_SLASH = Path.of(FilenameUtils.separatorsToSystem("/parent3/parent2/parent1/filename.txt"))
    static final Path FIVE_SEGMENT_FILE = Path.of(FilenameUtils.separatorsToSystem("parent4/parent3/parent2/parent1/filename.txt"))
    static final Path FIVE_SEGMENT_FILE_SLASH = Path.of(FilenameUtils.separatorsToSystem("/parent4/parent3/parent2/parent1/filename.txt"))

    static final String SAMPLE_TEXT_FILE_NAME = "sample-text-file.txt"
    static final String SAMPLE_TEXT_FILE_PACKAGE_PATH = FilenameUtils.separatorsToSystem("nz/govt/natlib/tools/sip/utils")
    static final String SAMPLE_TEXT_FILE_CONTENTS = "This is a sample text file."

    static final String DIRECTORY_MOVE_COPY_TESTS_PATH = FilenameUtils.separatorsToSystem("src/test/resources/directory-move-copy-tests")
    static Path WORKING_DIRECTORY

    List<Path> pathsToDelete

    static class PathUtilsTestCopyVisitor implements FileVisitor<Path> {
        // For this class, we throw all possible exceptions, as we want the test to fail if it fails.
        Path source
        Path target

        PathUtilsTestCopyVisitor(Path source, Path target) {
            this.source = source
            this.target = target
        }

        @Override
        FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            Path newDirectory= target.resolve(source.relativize(dir))
            Files.createDirectories(newDirectory)

            return FileVisitResult.CONTINUE
        }

        @Override
        FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Path newFile = target.resolve(source.relativize(file))
            Files.copy(file, newFile)

            return FileVisitResult.CONTINUE
        }

        @Override
        FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE
        }

        @Override
        FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE
        }
    }

    static class PathUtilsTestDeleteVisitor implements FileVisitor<Path> {
        // For this class, we throw all possible exceptions, as we want the test to fail if it fails.

        @Override
        FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return FileVisitResult.CONTINUE
        }

        @Override
        FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.deleteIfExists(file)

            return FileVisitResult.CONTINUE
        }

        @Override
        FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE
        }

        @Override
        FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.deleteIfExists(dir)
            return FileVisitResult.CONTINUE
        }
    }

    @BeforeClass
    static void setupStatic() {
        WORKING_DIRECTORY = Path.of(System.getProperty("user.dir"))
    }

    @Before
    void setup() {
        pathsToDelete = [ ]
    }

    @After
    void teardown() {
        pathsToDelete.each { Path path ->
            deleteDirectoryStructure(path)
        }
    }

    @Test
    void convertsFilenamesProperly() {
        checkAndConvertFilename("/this/is/a/path", "_this_is_a_path")
        checkAndConvertFilename("\\this\\is\\a\\path", "_this_is_a_path")
        checkAndConvertFilename("C:\\this\\is\\a\\path", "C__this_is_a_path")
        checkAndConvertFilename("dollars/\$are\$/removed", "dollars_-are-_removed")
        checkAndConvertFilename("/asterisks*have-/dashes*instead", "_asterisks-have-_dashes-instead")
        checkAndConvertFilename("no more spaces either ", "no-more-spaces-either-")
    }

    static void checkAndConvertFilename(String filePath, String expectedConversion) {
        assertThat("Correctly coverts=${filePath} to ${expectedConversion}",
                PathUtils.fileNameAsSafeString(filePath), is(expectedConversion))
    }

    @Test
    void verifyThatFilePathAsSafeStringWorksForDirectoryOnly() {
        int totalSegments = 1
        
        String expected = "filename.txt"
        String osPrefix = getOsPrefix()
        // Can't really test ZERO_SEGMENT_FILE, since it will automatically have its parent because of how File is constructed.
        // For ZERO_SEGMENT_FILE_SLASH, it's treated as the root folder, which means it is a blank
        assertThat("Full=${ZERO_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=''",
                PathUtils.filePathAsSafeString(ZERO_SEGMENT_FILE_SLASH, totalSegments), is("".toString()))
        assertThat("Full=${ONE_SEGMENT_FILE} with totalSegments=${totalSegments} is=filename.txt",
                PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE, totalSegments), is("filename.txt"))
        assertThat("Full=${ONE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE_SLASH, totalSegments), is(expected))
        assertThat("Full=${TWO_SEGMENT_FILE} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(TWO_SEGMENT_FILE, totalSegments), is(expected))
        assertThat("Full=${TWO_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(TWO_SEGMENT_FILE_SLASH, totalSegments), is(expected))
        assertThat("Full=${THREE_SEGMENT_FILE} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(THREE_SEGMENT_FILE, totalSegments), is(expected))
        assertThat("Full=${THREE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(THREE_SEGMENT_FILE_SLASH, totalSegments), is(expected))
        assertThat("Full=${FOUR_SEGMENT_FILE} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(FOUR_SEGMENT_FILE, totalSegments), is(expected))
        assertThat("Full=${FOUR_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(FOUR_SEGMENT_FILE_SLASH, totalSegments), is(expected))
        assertThat("Full=${FIVE_SEGMENT_FILE} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(FIVE_SEGMENT_FILE, totalSegments), is(expected))
        assertThat("Full=${FIVE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(FIVE_SEGMENT_FILE_SLASH, totalSegments), is(expected))
    }

    @Test
    void verifyThatFilePathAsSafeStringWorksForDirectoryAndOneParent() {
        int totalSegments = 2

        String expected = "parent1_filename.txt"
        String osPrefix = getOsPrefix()
        assertTrue("Full=${ONE_SEGMENT_FILE} with totalSegments=${totalSegments} ends with=filename.txt, actual=${PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE, totalSegments)}",
                PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE, totalSegments).endsWith("filename.txt"))
        assertThat("Full=${ONE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=filename.txt",
                PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE_SLASH, totalSegments), is("filename.txt".toString()))
        assertThat("Full=${TWO_SEGMENT_FILE} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(TWO_SEGMENT_FILE, totalSegments), is(expected))
        assertThat("Full=${TWO_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(TWO_SEGMENT_FILE_SLASH, totalSegments), is(expected))
        assertThat("Full=${THREE_SEGMENT_FILE} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(THREE_SEGMENT_FILE, totalSegments), is(expected))
        assertThat("Full=${THREE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(THREE_SEGMENT_FILE_SLASH, totalSegments), is(expected))
        assertThat("Full=${FOUR_SEGMENT_FILE} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(FOUR_SEGMENT_FILE, totalSegments), is(expected))
        assertThat("Full=${FOUR_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(FOUR_SEGMENT_FILE_SLASH, totalSegments), is(expected))
        assertThat("Full=${FIVE_SEGMENT_FILE} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(FIVE_SEGMENT_FILE, totalSegments), is(expected))
        assertThat("Full=${FIVE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(FIVE_SEGMENT_FILE_SLASH, totalSegments), is(expected))
    }

    @Test
    void verifyThatFilePathAsSafeStringWorksForDirectoryAndTwoParents() {
        int totalSegments = 3

        String expected = "parent2_parent1_filename.txt"
        String osPrefix = getOsPrefix()
        assertTrue("Full=${ONE_SEGMENT_FILE} with totalSegments=${totalSegments} ends with=filename.txt, actual=${PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE, totalSegments)}",
                PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE, totalSegments).endsWith("filename.txt"))
        assertThat("Full=${ONE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=filename.txt",
                PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE_SLASH, totalSegments), is("filename.txt".toString()))
        assertTrue("Full=${TWO_SEGMENT_FILE} with totalSegments=${totalSegments} ends with=parent1_filename.txt, actual=${PathUtils.filePathAsSafeString(TWO_SEGMENT_FILE, totalSegments)}",
                PathUtils.filePathAsSafeString(TWO_SEGMENT_FILE, totalSegments).endsWith("parent1_filename.txt"))
        assertThat("Full=${TWO_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=parent1_filename.txt",
                PathUtils.filePathAsSafeString(TWO_SEGMENT_FILE_SLASH, totalSegments), is("parent1_filename.txt".toString()))
        assertThat("Full=${THREE_SEGMENT_FILE} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(THREE_SEGMENT_FILE, totalSegments), is(expected))
        assertThat("Full=${THREE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(THREE_SEGMENT_FILE_SLASH, totalSegments), is(expected))
        assertThat("Full=${FOUR_SEGMENT_FILE} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(FOUR_SEGMENT_FILE, totalSegments), is(expected))
        assertThat("Full=${FOUR_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(FOUR_SEGMENT_FILE_SLASH, totalSegments), is(expected))
        assertThat("Full=${FIVE_SEGMENT_FILE} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(FIVE_SEGMENT_FILE, totalSegments), is(expected))
        assertThat("Full=${FIVE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(FIVE_SEGMENT_FILE_SLASH, totalSegments), is(expected))
    }

    @Test
    void verifyThatFilePathAsSafeStringWorksForDirectoryAndThreeParents() {
        int totalSegments = 4
        
        String expected = "parent3_parent2_parent1_filename.txt"
        String osPrefix = getOsPrefix()
        assertTrue("Full=${ONE_SEGMENT_FILE} with totalSegments=${totalSegments} ends with=filename.txt, actual=${PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE, totalSegments)}",
                PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE, totalSegments).endsWith("filename.txt"))
        assertThat("Full=${ONE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=filename.txt",
                PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE_SLASH, totalSegments), is("filename.txt".toString()))
        assertTrue("Full=${TWO_SEGMENT_FILE} with totalSegments=${totalSegments} ends with=parent1_filename.txt, actual=${PathUtils.filePathAsSafeString(TWO_SEGMENT_FILE, totalSegments)}",
                PathUtils.filePathAsSafeString(TWO_SEGMENT_FILE, totalSegments).endsWith("parent1_filename.txt"))
        assertThat("Full=${TWO_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=parent1_filename.txt",
                PathUtils.filePathAsSafeString(TWO_SEGMENT_FILE_SLASH, totalSegments), is("parent1_filename.txt".toString()))
        assertTrue("Full=${THREE_SEGMENT_FILE} with totalSegments=${totalSegments} ends with=parent2_parent1_filename.txt, actual=${PathUtils.filePathAsSafeString(THREE_SEGMENT_FILE, totalSegments)}",
                PathUtils.filePathAsSafeString(THREE_SEGMENT_FILE, totalSegments).endsWith("parent2_parent1_filename.txt"))
        assertThat("Full=${THREE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=parent2_parent1_filename.txt}",
                PathUtils.filePathAsSafeString(THREE_SEGMENT_FILE_SLASH, totalSegments), is("parent2_parent1_filename.txt".toString()))
        assertThat("Full=${FOUR_SEGMENT_FILE} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(FOUR_SEGMENT_FILE, totalSegments), is(expected))
        assertThat("Full=${FOUR_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(FOUR_SEGMENT_FILE_SLASH, totalSegments), is(expected))
        assertThat("Full=${FIVE_SEGMENT_FILE} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(FIVE_SEGMENT_FILE, totalSegments), is(expected))
        assertThat("Full=${FIVE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=${expected}",
                PathUtils.filePathAsSafeString(FIVE_SEGMENT_FILE_SLASH, totalSegments), is(expected))
    }

    @Test
    void verifyThatFilePathAsSafeStringWorksForFullPath() {
        int totalSegments = 0

        String osPrefix = getOsPrefix()
        assertTrue("Full=${ONE_SEGMENT_FILE} with totalSegments=${totalSegments} ends with=filename.txt, actual=${PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE, totalSegments)}",
                PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE, totalSegments).endsWith("filename.txt"))
        assertThat("Full=${ONE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=filename.txt",
                PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE_SLASH, totalSegments), is("filename.txt".toString()))
        assertTrue("Full=${TWO_SEGMENT_FILE} with totalSegments=${totalSegments} ends with=parent1_filename.txt, actual=${PathUtils.filePathAsSafeString(TWO_SEGMENT_FILE, totalSegments)}",
                PathUtils.filePathAsSafeString(TWO_SEGMENT_FILE, totalSegments).endsWith("parent1_filename.txt"))
        assertThat("Full=${TWO_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=parent1_filename.txt",
                PathUtils.filePathAsSafeString(TWO_SEGMENT_FILE_SLASH, totalSegments), is("parent1_filename.txt".toString()))
        assertTrue("Full=${THREE_SEGMENT_FILE} with totalSegments=${totalSegments} ends with=parent2_parent1_filename.txt, actual=${PathUtils.filePathAsSafeString(THREE_SEGMENT_FILE, totalSegments)}",
                PathUtils.filePathAsSafeString(THREE_SEGMENT_FILE, totalSegments).endsWith("parent2_parent1_filename.txt"))
        assertThat("Full=${THREE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=parent2_parent1_filename.txt}",
                PathUtils.filePathAsSafeString(THREE_SEGMENT_FILE_SLASH, totalSegments), is("parent2_parent1_filename.txt".toString()))
        assertTrue("Full=${FOUR_SEGMENT_FILE} with totalSegments=${totalSegments} ends with=parent3_parent2_parent1_filename.txt, actual=${PathUtils.filePathAsSafeString(FOUR_SEGMENT_FILE, totalSegments)}",
                PathUtils.filePathAsSafeString(FOUR_SEGMENT_FILE, totalSegments).endsWith("parent3_parent2_parent1_filename.txt"))
        assertThat("Full=${FOUR_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=parent3_parent2_parent1_filename.txt}",
                PathUtils.filePathAsSafeString(FOUR_SEGMENT_FILE_SLASH, totalSegments), is("parent3_parent2_parent1_filename.txt".toString()))
        assertThat("Full=${FIVE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=parent4_parent3_parent2_parent1_filename.txt}",
                PathUtils.filePathAsSafeString(FIVE_SEGMENT_FILE_SLASH, totalSegments), is("parent4_parent3_parent2_parent1_filename.txt".toString()))
    }

    @Test
    void verifyThatFilePathAsSafeStringWorksForNoOption() {
        int totalSegments = 0

        String osPrefix = getOsPrefix()
        assertTrue("Full=${ONE_SEGMENT_FILE} with totalSegments=${totalSegments} ends with=filename.txt, actual=${PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE, totalSegments)}",
                PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE, totalSegments).endsWith("filename.txt"))
        assertThat("Full=${ONE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=filename.txt",
                PathUtils.filePathAsSafeString(ONE_SEGMENT_FILE_SLASH, totalSegments), is("filename.txt".toString()))
        assertTrue("Full=${TWO_SEGMENT_FILE} with totalSegments=${totalSegments} ends with=parent1_filename.txt, actual=${PathUtils.filePathAsSafeString(TWO_SEGMENT_FILE, totalSegments)}",
                PathUtils.filePathAsSafeString(TWO_SEGMENT_FILE, totalSegments).endsWith("parent1_filename.txt"))
        assertThat("Full=${TWO_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=parent1_filename.txt",
                PathUtils.filePathAsSafeString(TWO_SEGMENT_FILE_SLASH, totalSegments), is("parent1_filename.txt".toString()))
        assertTrue("Full=${THREE_SEGMENT_FILE} with totalSegments=${totalSegments} ends with=parent2_parent1_filename.txt, actual=${PathUtils.filePathAsSafeString(THREE_SEGMENT_FILE, totalSegments)}",
                PathUtils.filePathAsSafeString(THREE_SEGMENT_FILE, totalSegments).endsWith("parent2_parent1_filename.txt"))
        assertThat("Full=${THREE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=parent2_parent1_filename.txt}",
                PathUtils.filePathAsSafeString(THREE_SEGMENT_FILE_SLASH, totalSegments), is("parent2_parent1_filename.txt".toString()))
        assertTrue("Full=${FOUR_SEGMENT_FILE} with totalSegments=${totalSegments} ends with=parent3_parent2_parent1_filename.txt, actual=${PathUtils.filePathAsSafeString(FOUR_SEGMENT_FILE, totalSegments)}",
                PathUtils.filePathAsSafeString(FOUR_SEGMENT_FILE, totalSegments).endsWith("parent3_parent2_parent1_filename.txt"))
        assertThat("Full=${FOUR_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=parent3_parent2_parent1_filename.txt}",
                PathUtils.filePathAsSafeString(FOUR_SEGMENT_FILE_SLASH, totalSegments), is("parent3_parent2_parent1_filename.txt".toString()))
        assertTrue("Full=${FIVE_SEGMENT_FILE} with totalSegments=${totalSegments} ends with=parent4_parent3_parent2_parent1_filename.txt, actual=${PathUtils.filePathAsSafeString(FIVE_SEGMENT_FILE, totalSegments)}",
                PathUtils.filePathAsSafeString(FIVE_SEGMENT_FILE, totalSegments).endsWith("parent4_parent3_parent2_parent1_filename.txt"))
        assertThat("Full=${FIVE_SEGMENT_FILE_SLASH} with totalSegments=${totalSegments} is=parent4_parent3_parent2_parent1_filename.txt}",
                PathUtils.filePathAsSafeString(FIVE_SEGMENT_FILE_SLASH, totalSegments), is("parent4_parent3_parent2_parent1_filename.txt".toString()))
    }

    @Test
    void canWritesSampleRootResourceFileToTempFolder() {
        Path tempFile = PathUtils.writeResourceToTemporaryDirectory(SAMPLE_TEXT_FILE_NAME,
                "PathUtilsTest-unit-test_", "", SAMPLE_TEXT_FILE_NAME, null)
        pathsToDelete.add(tempFile)

        String expectedContents = SAMPLE_TEXT_FILE_CONTENTS

        assertTrue("tempFile=${tempFile.normalize()} exists", Files.exists(tempFile))

        String contents = tempFile.text
        assertThat("Temp file contents=${contents} matches=${expectedContents}", contents, is(expectedContents))

        pathsToDelete.add(tempFile.parent)
    }

    @Test
    void canWritesSamplePackageResourceFileToTempFolder() {
        Path tempFile = PathUtils.writeResourceToTemporaryDirectory(SAMPLE_TEXT_FILE_NAME,
                "PathUtilsTest-unit-test_", SAMPLE_TEXT_FILE_PACKAGE_PATH,
                SAMPLE_TEXT_FILE_NAME, null)
        pathsToDelete.add(tempFile)

        String expectedContents = SAMPLE_TEXT_FILE_CONTENTS

        assertTrue("tempFile=${tempFile.normalize()} exists", Files.exists(tempFile))

        String contents = tempFile.text
        assertThat("Temp file contents=${contents} matches=${expectedContents}", contents, is(expectedContents))
    }

    static String getOsPrefix() {
        String osPrefix
        if (SystemUtils.IS_OS_WINDOWS) {
            Path testFile = Path.of("")
            List<String> splitPath = testFile.normalize().toString().split(Pattern.quote(File.separator))
            osPrefix = splitPath.first().replace(":", "_")
        } else {
            osPrefix = ""
        }

        return osPrefix
    }

    @Test
    void atomicDirectoryMoveDirectoryWorksCorrectly() {
        directoryCopyOrMoveWorksCorrectly(true, true)
    }

    @Test
    void atomicDirectoryCopyDirectoryWorksCorrectly() {
        directoryCopyOrMoveWorksCorrectly(false, true)
    }

    @Test
    void directoryMoveDirectoryWorksCorrectly() {
        directoryCopyOrMoveWorksCorrectly(true, false)
    }

    @Test
    void directoryCopyDirectoryWorksCorrectly() {
        directoryCopyOrMoveWorksCorrectly(false, false)
    }

    void directoryCopyOrMoveWorksCorrectly(boolean move, boolean useAtomic) {
        Path sourceDirectory = setupSourceDirectory()
        pathsToDelete.add(sourceDirectory)
        assertThat("sourceDirectory=${sourceDirectory} exists", Files.exists(sourceDirectory), is(true))

        UUID uuid = UUID.randomUUID()
        Path tempTargetLocation = org.apache.commons.io.FileUtils.tempDirectory.toPath().resolve("atomic-copy-directory-test_" + uuid)
        pathsToDelete.add(tempTargetLocation)

        PathUtils.atomicMoveOrCopyDirectory(move, sourceDirectory, tempTargetLocation, useAtomic, false, null)

        if (move) {
            assertThat("sourceDirectory=${sourceDirectory} no longer exists", Files.notExists(sourceDirectory), is(true))
        } else {
            verifyDirectoryStructure(sourceDirectory)
        }
        verifyDirectoryStructure(tempTargetLocation)
    }

    Path setupSourceDirectory() {
        Path tempTargetLocation = Files.createTempDirectory(org.apache.commons.io.FileUtils.tempDirectory.toPath(),
                "atomic-move-directory-test_")
        Path sourceLocation = WORKING_DIRECTORY.resolve(DIRECTORY_MOVE_COPY_TESTS_PATH)
        PathUtilsTestCopyVisitor copyVisitor = new PathUtilsTestCopyVisitor(sourceLocation, tempTargetLocation)

        Files.walkFileTree(sourceLocation, copyVisitor)

        return tempTargetLocation
    }

    void deleteDirectoryStructure(Path rootDirectory) {
        PathUtilsTestDeleteVisitor deleteVisitor = new PathUtilsTestDeleteVisitor()
        Files.walkFileTree(rootDirectory, deleteVisitor)
        Files.deleteIfExists(rootDirectory)
    }

    void verifyDirectoryStructure(Path rootPath) {
        Path directoryMoveCopyTests = rootPath

        Path subdirectory1 = directoryMoveCopyTests.resolve("subdirectory-1")
        String testFileSubdirectory1NameAndContents = "test-file-subdirectory-1.txt"
        Path testFileSubdirectory1 = subdirectory1.resolve(testFileSubdirectory1NameAndContents)
        Path subSub11 = subdirectory1.resolve("sub-sub-1-1")
        String testFileSubSub11NameAndContents = "test-file-sub-sub-1-1.txt"
        Path testFileSubSub11 = subSub11.resolve(testFileSubSub11NameAndContents)
        Path subSub12 = subdirectory1.resolve("sub-sub-1-2")
        String testFileSubSub12NameAndContents = "test-file-sub-sub-1-2.txt"
        Path testFileSubSub12 = subSub12.resolve(testFileSubSub12NameAndContents)
        Path testFileSubSub12Empty = subSub12.resolve("test-file-sub-sub-1-2-EMPTY.txt")

        Path subdirectory2 = directoryMoveCopyTests.resolve("subdirectory-2")
        String testFileSubdirectory2NameAndContents = "test-file-subdirectory-2.txt"
        Path testFileSubdirectory2 = subdirectory2.resolve(testFileSubdirectory2NameAndContents)
        Path subSub21 = subdirectory2.resolve("sub-sub-2-1")
        String testFileSubSub21NameAndContents = "test-file-sub-sub-2-1.txt"
        Path testFileSubSub21 = subSub21.resolve(testFileSubSub21NameAndContents)

        assertTrue("rootPath=${rootPath} exists", rootPath.toFile().exists())
        Collection<File> allFiles = org.apache.commons.io.FileUtils.listFilesAndDirs(rootPath.toFile(), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE)
        // Note that the rootPath is also included
        assertThat("${rootPath.toFile().canonicalPath} Total files and folders size=${allFiles.size()}", allFiles.size(), is(12))

        assertTrue("subdirectory1=${subdirectory1} exists", Files.exists(subdirectory1))
        assertTrue("testFileSubdirectory1=${testFileSubdirectory1} exists", Files.exists(testFileSubdirectory1))
        assertThat("testFileSubdirectory1 text=${testFileSubdirectory1NameAndContents}", testFileSubdirectory1.text, is(testFileSubdirectory1NameAndContents))
        assertTrue("subSub11=${subSub11} exists", Files.exists(subSub11))
        assertTrue("testFileSubSub11=${testFileSubSub11} exists", Files.exists(testFileSubSub11))
        assertThat("testFileSubSub11 text=${testFileSubSub11NameAndContents}", testFileSubSub11.text, is(testFileSubSub11NameAndContents))
        assertTrue("subSub12=${subSub12} exists", Files.exists(subSub12))
        assertTrue("testFileSubSub12=${testFileSubSub12} exists", Files.exists(testFileSubSub12))
        assertThat("testFileSubSub12 text=${testFileSubSub12NameAndContents}", testFileSubSub12.text, is(testFileSubSub12NameAndContents))
        assertTrue("testFileSubSub12Empty=${testFileSubSub12Empty} exists", Files.exists(testFileSubSub12Empty))
        assertThat("testFileSubSub12Empty is empty", testFileSubSub12Empty.text, is(""))

        assertTrue("subdirectory2=${subdirectory2} exists", Files.exists(subdirectory2))
        assertTrue("testFileSubdirectory2=${testFileSubdirectory2} exists", Files.exists(testFileSubdirectory2))
        assertThat("testFileSubdirectory2 text=${testFileSubdirectory2NameAndContents}", testFileSubdirectory2.text, is(testFileSubdirectory2NameAndContents))
        assertTrue("subSub21=${subSub21} exists", Files.exists(subSub21))
        assertTrue("testFileSubSub21=${testFileSubSub21} exists", Files.exists(testFileSubSub21))
        assertThat("testFileSubSub21 text=${testFileSubSub21NameAndContents}", testFileSubSub21.text, is(testFileSubSub21NameAndContents))
    }

    @Test
    void correctlySplitsPathIntoSegments() {
        verifyPathSplit(Path.of("abc/def/ghi/jkl"), [ "abc", "def", "ghi", "jkl" ])
        verifyPathSplit(Path.of(""), [ "" ])
        verifyPathSplit(Path.of("abc"), [ "abc" ])
    }

    void verifyPathSplit(Path testPath, List<String> expectedSegments) {
        List<String> segments = PathUtils.asSegments(testPath)
        assertThat("testPath=${testPath}, segments=${segments}, size=${segments.size()}", segments.size(), is(expectedSegments.size()))
        expectedSegments.eachWithIndex { String expectedSegment, int index ->
            assertThat("index=${index}, segment=${segments.get(index)}", segments.get(index), is(expectedSegments.get(index)))
        }
    }
}
