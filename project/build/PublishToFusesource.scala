import sbt._

import java.io.File

trait PublishToFusesource extends BasicManagedProject {
  final override def managedStyle = ManagedStyle.Maven
  final val fusesourceRelease = "Fusesource Release Repository" at "http://repo.fusesource.com/nexus/service/local/staging/deploy/maven2"
  final val publishTo = fusesourceRelease
  Credentials(Path.userHome / ".ivy2" / "repo.fusesource.com", log)
}
