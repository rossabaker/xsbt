import sbt._

import java.io.File

class XSbt(info: ProjectInfo) extends ParentProject(info) with NoCrossPaths with PublishToFusesource
{
		/* Subproject declarations*/

	val launchInterfaceSub = project(launchPath / "interface", "Launcher Interface", new LaunchInterfaceProject(_))
	val launchSub = project(launchPath, "Launcher", new LaunchProject(_), launchInterfaceSub)

	val interfaceSub = project("interface", "Interface", new InterfaceProject(_))
	val apiSub = baseProject(compilePath / "api", "API", interfaceSub)

	val controlSub = baseProject(utilPath / "control", "Control")
	val collectionSub = baseProject(utilPath / "collection", "Collections")
	val ioSub = project(utilPath / "io", "IO", new IOProject(_), controlSub)
	val classpathSub = baseProject(utilPath / "classpath", "Classpath")

	val ivySub = project("ivy", "Ivy", new IvyProject(_), interfaceSub, launchInterfaceSub)
	val logSub = baseProject(utilPath / "log", "Logging", interfaceSub)
	val datatypeSub = baseProject("util" /"datatype", "Datatype Generator", ioSub)

	val testSub = project("scripted", "Test", new TestProject(_), ioSub)

	val compileInterfaceSub = project(compilePath / "interface", "Compiler Interface", new CompilerInterfaceProject(_), interfaceSub)

	val taskSub = project(tasksPath, "Tasks", new TaskProject(_), controlSub, collectionSub)
	val cacheSub = project(cachePath, "Cache", new CacheProject(_), taskSub, ioSub)
	val trackingSub = baseProject(cachePath / "tracking", "Tracking", cacheSub)
	val compilerSub = project(compilePath, "Compile", new CompileProject(_),
		launchInterfaceSub, interfaceSub, ivySub, ioSub, classpathSub, compileInterfaceSub)
	val stdTaskSub = project(tasksPath / "standard", "Standard Tasks", new StandardTaskProject(_), trackingSub, compilerSub, apiSub)

	val altCompilerSub = baseProject("main", "Alternate Compiler Test", stdTaskSub, logSub)

	val sbtSub = project(sbtPath, "Simple Build Tool", new SbtProject(_) {}, compilerSub, launchInterfaceSub)
	val installerSub = project(sbtPath / "install", "Installer", new InstallerProject(_) {}, sbtSub)

	def baseProject(path: Path, name: String, deps: Project*) = project(path, name, new Base(_), deps : _*)
	
		/* Multi-subproject paths */
	def sbtPath = path("sbt")
	def cachePath = path("cache")
	def tasksPath = path("tasks")
	def launchPath = path("launch")
	def utilPath = path("util")
	def compilePath = path("compile")

	def compilerInterfaceClasspath = compileInterfaceSub.projectClasspath(Configurations.Test)

	//run in parallel
	override def parallelExecution = true
	override def shouldCheckOutputDirectories = false

	def jlineDep = "jline" % "jline" % "0.9.94" intransitive()

		/* Subproject configurations*/
	class LaunchProject(info: ProjectInfo) extends Base(info) with TestWithIO with TestDependencies with ProguardLaunch with NoCrossPaths
	{
		val jline = jlineDep
		val ivy = "org.apache.ivy" % "ivy" % "2.2.0"
		override def deliverProjectDependencies = Nil

		// defines the package that proguard operates on
		def rawJarPath = jarPath
		def rawPackage = `package`
		override def packagePaths = super.packagePaths +++ launchInterfaceSub.packagePaths

		// configure testing
		override def testClasspath = super.testClasspath +++ interfaceSub.compileClasspath +++ interfaceSub.mainResourcesPath
		override def testCompileAction = super.testCompileAction dependsOn(interfaceSub.publishLocal, testSamples.publishLocal)

		// used to test the retrieving and loading of an application: sample app is packaged and published to the local repository
		lazy val testSamples = project("test-sample", "Launch Test", new TestSamples(_), interfaceSub, launchInterfaceSub)
		class TestSamples(info: ProjectInfo) extends Base(info) with NoCrossPaths with NoRemotePublish {
			override def deliverProjectDependencies = Nil
		}
	}
	trait TestDependencies extends Project
	{
		val sc = "org.scala-tools.testing" %% "scalacheck" % "1.6" % "test"
		val sp = "org.scala-tools.testing" % "specs" % "1.6.0" % "test"
	}
	class StandardTaskProject(info: ProjectInfo) extends Base(info)
	{
		override def testClasspath = super.testClasspath +++ compilerSub.testClasspath --- compilerInterfaceClasspath
	}

	class IOProject(info: ProjectInfo) extends Base(info) with TestDependencies
	class TaskProject(info: ProjectInfo) extends Base(info) with TestDependencies
	class CacheProject(info: ProjectInfo) extends Base(info)
	{
		// these compilation options are useful for debugging caches and task composition
		//override def compileOptions = super.compileOptions ++ List(Unchecked,ExplainTypes, CompileOption("-Xlog-implicits"))
		val sbinary = "org.scala-tools.sbinary" %% "sbinary" % "0.3"
	}
	class Base(info: ProjectInfo) extends DefaultProject(info) with ManagedBase with Component with Licensed
	{
		override def scratch = true
		override def consoleClasspath = testClasspath
	}
	trait Licensed extends BasicScalaProject
	{
		def notice = path("NOTICE")
		abstract override def mainResources = super.mainResources +++ notice +++ Path.lazyPathFinder( extractLicenses )
		lazy val seeRegex = """\(see (.*?)\)""".r
		def licensePath(str: String): Path = { val path = Path.fromString(XSbt.this.info.projectPath, str); if(path.exists) path else error("Referenced license '" + str + "' not found at " + path) }
		def seePaths(noticeString: String): List[Path] = seeRegex.findAllIn(noticeString).matchData.map(d => licensePath(d.group(1))).toList
		def extractLicenses = if(!notice.exists) Nil else FileUtilities.readString(notice asFile, log).fold(_ => { log.warn("Could not read NOTICE"); Nil} , seePaths _)
	}
	class CompileProject(info: ProjectInfo) extends Base(info) with TestWithLog with TestWithLaunch
	{
		override def testCompileAction = super.testCompileAction dependsOn(compileInterfaceSub.`package`, interfaceSub.`package`)
		override def testClasspath = super.testClasspath +++ compileInterfaceSub.packageSrcJar +++ interfaceSub.jarPath --- compilerInterfaceClasspath --- interfaceSub.mainCompilePath
		override def compileOptions = super.compileOptions ++ Seq(CompileOption("-Xno-varargs-conversion")) //needed for invoking nsc.scala.tools.Main.process(Array[String])
	}
	class IvyProject(info: ProjectInfo) extends Base(info) with TestWithIO with TestWithLog with TestWithLaunch
	{
		val ivy = "org.apache.ivy" % "ivy" % "2.2.0"
	}
	abstract class BaseInterfaceProject(info: ProjectInfo) extends DefaultProject(info) with ManagedBase with TestWithLog with Component with JavaProject
	class InterfaceProject(info: ProjectInfo) extends BaseInterfaceProject(info)
	{
		override def componentID: Option[String] = Some("xsbti")
		override def packageAction = super.packageAction dependsOn generateVersions
		def versionPropertiesPath = mainResourcesPath / "xsbt.version.properties"
		lazy val generateVersions = task {
			import java.util.{Date, TimeZone}
			val formatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")
			formatter.setTimeZone(TimeZone.getTimeZone("GMT"))
			val timestamp = formatter.format(new Date)
			val content = "version=" + version + "\ntimestamp=" + timestamp
			log.info("Writing version information to " + versionPropertiesPath + " :\n" + content)
			FileUtilities.write(versionPropertiesPath.asFile, content, log)
		}

		override def watchPaths = super.watchPaths +++ apiDefinitionPaths --- sources(generatedBasePath)
		override def mainSourceRoots = super.mainSourceRoots +++ (generatedBasePath ##)
		def srcManagedPath = path("src_managed")
		def generatedBasePath = srcManagedPath / "main" / "java"
		/** Files that define the datatypes.*/
		def apiDefinitionPaths: PathFinder = "definition"
		/** Delete up the generated sources*/
		lazy val cleanManagedSrc = cleanTask(srcManagedPath)
		override def cleanAction = super.cleanAction dependsOn(cleanManagedSrc)
		/** Runs the generator compiled by 'compile', putting the classes in src_managed and processing the definitions 'apiDefinitions'. */
		lazy val generateSource = generateSourceAction dependsOn(cleanManagedSrc, datatypeSub.compile)
		def generateSourceTask(immutable: Boolean, pkg: String, apiDefinitions: PathFinder): Task =
		{
			val m = if(immutable) "immutable" else "mutable"
			generateSourceTask(m :: pkg :: generatedBasePath.absolutePath :: apiDefinitions.get.toList.map(_.absolutePath))
		}
		def generateSourceTask(args: List[String]): Task =
			runTask(datatypeSub.getMainClass(true), datatypeSub.runClasspath, args)
		def generateSourceAction =
			//generateSourceTask(false, "xsbti.api", "definition" +++ "type") &&
			generateSourceTask(true, "xsbti.api", "other" +++ "definition" +++ "type")
		/** compiles the generated sources */
		override def compileAction = super.compileAction dependsOn(generateSource)
	}
	class LaunchInterfaceProject(info: ProjectInfo) extends BaseInterfaceProject(info)
	{
		override def componentID = None
	}
	class TestProject(info: ProjectInfo) extends Base(info)
	{
		val process = "org.scala-tools.sbt" % "process" % "0.1"
	}
	class CompilerInterfaceProject(info: ProjectInfo) extends Base(info) with PrecompiledInterface with NoCrossPaths with TestWithIO with TestWithLog
	{ cip => 
		//val jline = jlineDep artifacts(Artifact("jline", Map("e:component" -> srcID)))
		// necessary because jline is not distributed with 2.8 and we will get a compile error 
		// sbt should work with the above inline declaration, but it doesn't, so the inline Ivy version is used for now.
		override def ivyXML =
			( <publications />
			<dependencies>
				<dependency org="jline" name="jline" rev="0.9.94" transitive="false">
					<artifact name="jline" type="jar" e:component={srcID}/>
				</dependency>
			</dependencies> )

		def xTestClasspath =  projectClasspath(Configurations.Test)

		def srcID = "compiler-interface-src"
		lazy val srcArtifact = Artifact(srcID) extra("e:component" -> srcID)
		override def packageSrcJar = mkJarPath(srcID)
		lazy val pkgSrc = packageSrc // call it something else because we don't need dependencies to run package-src
		override def packageAction = super.packageAction dependsOn(pkgSrc)
		
		// sub projects for each version of Scala to precompile against other than the one sbt is built against
		// each sub project here will add ~100k to the download
		lazy val precompiled280 = precompiledSub("2.8.0")
		lazy val precompiled281 = precompiledSub("2.8.1.RC4")

		def precompiledSub(v: String) = 
			project(info.projectPath, "Precompiled " + v, new Precompiled(v)(_), cip.info.dependencies.toSeq : _* /*doesn't include subprojects of cip*/ )

		/** A project that compiles the compiler interface against the Scala version 'sv'.
		* This is done for selected Scala versions (generally, popular ones) so that it doesn't need to be done at runtime. */
		class Precompiled(sv: String)(info: ProjectInfo) extends Base(info) with PrecompiledInterface with NoUpdate {
			/** force the Scala version in order to precompile the compiler interface for different Scala versions*/
			override def buildScalaVersion = sv

			/** Get compilation classpath from parent.  Scala dependencies are added on top of this and this
			* subproject does not depend on any Scala subprojects, so mixing versions is not a problem. */
			override def compileClasspath = cip.compileClasspath --- cip.mainUnmanagedClasspath +++ mainUnmanagedClasspath

			// these ensure that the classes compiled against other versions of Scala are not exported (for compilation/testing/...)
			override def projectClasspath(config: Configuration) = Path.emptyPathFinder
		}
	}
	trait TestWithIO extends TestWith {
		override def testWithTestClasspath = super.testWithTestClasspath ++ Seq(ioSub)
	}
	trait TestWithLaunch extends TestWith {
		override def testWithTestClasspath = super.testWithTestClasspath ++ Seq(launchSub)
	}
	trait TestWithLog extends TestWith {
		override def testWithCompileClasspath = super.testWithCompileClasspath ++ Seq(logSub)
	}
	trait TestWith extends BasicScalaProject
	{
		def testWithCompileClasspath: Seq[BasicScalaProject] = Nil
		def testWithTestClasspath: Seq[BasicScalaProject] = Nil
		override def testCompileAction = super.testCompileAction dependsOn((testWithTestClasspath.map(_.testCompile) ++ testWithCompileClasspath.map(_.compile)) : _*)
		override def testClasspath = (super.testClasspath /: (testWithTestClasspath.map(_.testClasspath) ++  testWithCompileClasspath.map(_.compileClasspath) ))(_ +++ _)
	}
}
