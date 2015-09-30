import java.io.File

import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import org.apache.maven.shared.invoker.{DefaultInvocationRequest, DefaultInvoker}

import scala.collection.mutable.ListBuffer
import scala.xml.XML

/**
 * @author Adedayo Adetoye.
 */

copyJars

def copyJars = {
  var projectDirectories = Set.empty[Path]
  val path = new File("").toPath
  val jars = findJarFiles(path)
  jars.par.filter(jar => {
    jar.getParent.endsWith("target")
  } && jar.startsWith("source")
  ).foreach(jar => {
    val name = jar.getFileName.toString
    val index0 = name.indexOf('-')
    val index = name.indexOf("-SNAPSHOT")

    if (index != -1) {
      val dir = name.substring(0, index0)
      projectDirectories += Paths.get(s"out_maven/$dir")
      val target = Paths.get(s"out_maven/$dir/target/")
      if (!Files.exists(target)) {
        Files.createDirectories(target)
      }

      val file = jar.getParent.relativize(
        if (name.contains("source") || name.contains("src"))
          Paths.get(dir + "-sources.jar")
        else if (name.contains("javadoc"))
          Paths.get(dir + "-javadoc.jar")
        else
          Paths.get(dir + ".jar")
      )
      val src = file.getParent
      Files.copy(jar, target.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING)
      val version = name.substring(index0+1, index)
      XML.save(s"out_maven/$dir/pom.xml", generatePOM(dir, version))
    }
  })
  deploy(projectDirectories.toList)
}


def deploy(directories: List[Path]) = {
  println("Deploying Projects ...")
  directories.par.foreach(dir => {
    val invoker = new DefaultInvoker
    val request = new DefaultInvocationRequest
    request.setPomFile(new File(s"$dir/pom.xml"))
    import scala.collection.JavaConversions._
    val goals = List(
      "org.apache.maven.plugins:maven-jar-plugin:jar"
      ,"org.apache.maven.plugins:maven-source-plugin:jar-no-fork"
      ,"org.apache.maven.plugins:maven-javadoc-plugin:jar"
      ,"org.apache.maven.plugins:maven-gpg-plugin:sign"
      ,"org.apache.maven.plugins:maven-install-plugin:2.3.1:install"
      ,"org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy"
    )
    request.setGoals(goals)
    invoker.execute(request)
  })
}



def findJarFiles(paths: Path*): List[Path] = {
  find(paths: _*)("glob:*.jar")
}

def find(paths: Path*)(glob: String): List[Path] = {
  val files = ListBuffer[Path]()
  def append(name: Path): Unit = files.synchronized {
    files += name
  }
  paths.par.foreach(path => Files.walkFileTree(path, new FileFinder(append)(glob)(FileSystems.getDefault)))
  files.toList
}

class FileFinder(callback: Path => Unit)(ext: String)(fileSystem: FileSystem = FileSystems.getDefault)
  extends SimpleFileVisitor[Path] {
  val matcher = fileSystem.getPathMatcher(ext)

  override def visitFile(path: Path, attr: BasicFileAttributes): FileVisitResult = {
    val name = path.getFileName
    if (name != null && matcher.matches(name)) callback(path)
    FileVisitResult.CONTINUE
  }
}


class CopyDirVisitor(src: Path, dest: Path) extends SimpleFileVisitor[Path] {
  override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = {
    val target = dest.resolve(src.relativize(dir))
    if (!Files.exists(target)) Files.createDirectory(target)
    FileVisitResult.CONTINUE
  }

  override def visitFile(file: Path, attrs: BasicFileAttributes) = {
    Files.copy(file, dest.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING)
    FileVisitResult.CONTINUE
  }
}


def generatePOM(project: String, version: String) = {
  val branch = version.replace(".SNAPSHOT", "")
  val pom =
    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
      <modelVersion>4.0.0</modelVersion>
      <groupId>com.github.adedayo.eclipse.sdk</groupId>
      <artifactId> {project} </artifactId>
      <version> {branch} </version>
      <packaging>jar</packaging>
      <name> {project} </name>
      <url>http://www.eclipse.org</url>
      <description>A packaging of the eclipse sdk {project} library.</description>

      <licenses>
        <license>
          <name>Eclipse Public License (EPL) v1.0</name>
          <url>http://wiki.eclipse.org/EPL</url>
        </license>
      </licenses>


      <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.javadoc.failOnError>false</maven.javadoc.failOnError>
      </properties>

      <developers>
        <developer>
          <id>adedayo</id>
          <name>Adedayo Adetoye</name>
          <email>dayo.dev@gmail.com</email>
        </developer>
      </developers>

      <scm>
        <connection>scm:git:git@github.com:adedayo/eclipse-sdk.git</connection>
        <developerConnection>scm:git:git@github.com:adedayo/eclipse-sdk.git</developerConnection>
        <url>https://github.com/adedayo/eclipse-sdk.git</url>
      </scm>

      <distributionManagement>
        <snapshotRepository>
          <id>ossrh</id>
          <url>https://oss.sonatype.org/content/repositories/snapshots</url>
        </snapshotRepository>
        <repository>
          <id>ossrh</id>
          <url>https://oss.sonatype.org/service/local/staging/deploy/maven2/</url>
        </repository>
      </distributionManagement>

      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-gpg-plugin</artifactId>
            <version>1.6</version>
            <executions>
              <execution>
                <id>sign-artifacts</id>
                <phase>package</phase>
                <goals>
                  <goal>sign</goal>
                </goals>
              </execution>
            </executions>
          </plugin>
          <plugin>
            <groupId>org.sonatype.plugins</groupId>
            <artifactId>nexus-staging-maven-plugin</artifactId>
            <version>1.6.5</version>
            <extensions>true</extensions>
            <configuration>
              <serverId>ossrh</serverId>
              <nexusUrl>https://oss.sonatype.org/</nexusUrl>
              <autoReleaseAfterClose>true</autoReleaseAfterClose>
            </configuration>
          </plugin>
        </plugins>
      </build>
    </project>

  pom
}

