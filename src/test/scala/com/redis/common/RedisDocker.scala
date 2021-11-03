package com.redis.common

import java.io.File

import com.whisk.docker.impl.dockerjava.DockerKitDockerJava
import com.whisk.docker.scalatest.DockerTestKit
import com.whisk.docker.{DockerContainer, DockerKit, DockerReadyChecker, VolumeMapping}
import org.apache.commons.lang.RandomStringUtils
import org.scalatest.Suite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Seconds, Span}


trait RedisDockerCluster extends RedisContainer {
  that: Suite =>

  protected def redisContainerPort(container: DockerContainer): Int = container.getPorts().futureValue.apply(redisPort)

  protected lazy val runningContainers: List[DockerContainer] = (0 until 4)
    .map(_ => createContainer())
    .toList

  abstract override def dockerContainers: List[DockerContainer] =
    runningContainers ++ super.dockerContainers

}

trait RedisDocker extends RedisContainer {
  that: Suite =>

  protected lazy val redisContainerPort: Int = runningContainer.getPorts().futureValue.apply(redisPort)

  private lazy val runningContainer = createContainer()

  abstract override def dockerContainers: List[DockerContainer] =
    runningContainer :: super.dockerContainers

}

trait RedisDockerSSL extends RedisDocker {
  that: Suite =>

  private val certsPath = new File("src/test/resources/certs").getAbsolutePath

  override protected def baseContainer(name: Option[String]) =
    DockerContainer("madflojo/redis-tls:latest", name = name)
      .withVolumes(Seq(VolumeMapping(certsPath, "/certs")))
}

trait RedisContainer extends DockerKit with DockerTestKit with DockerKitDockerJava with ScalaFutures {
  that: Suite =>

  implicit val pc: PatienceConfig = PatienceConfig(Span(30, Seconds), Span(100, Milliseconds))

  protected val redisContainerHost: String = "localhost"
  protected val redisPort: Int = 6379
  protected val redisPassword: Option[String] = None

  // just password supported for now
  private def args = redisPassword.map(password => Seq("--requirepass", password))

  protected def baseContainer(name: Option[String]) = DockerContainer("redis:latest", name=name, command=args)

  protected def createContainer(name: Option[String] = Some(RandomStringUtils.randomAlphabetic(10)),
                                ports: Map[Int, Int] = Map.empty): DockerContainer = {
    val containerPorts: Seq[(Int, Option[Int])] = if (ports.isEmpty) {
      Seq((redisPort -> None))
    } else {
      ports.mapValues(i => Some(i)).toSeq
    }

    baseContainer(name).withPorts(containerPorts: _*)
      .withReadyChecker(DockerReadyChecker.LogLineContains("Ready to accept connections"))
  }
}
