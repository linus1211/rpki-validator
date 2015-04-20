/**
 * The BSD License
 *
 * Copyright (c) 2010-2012 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.validator.models.validation

import scala.language.existentials
import java.io.File
import java.net.URI

import net.ripe.rpki.commons.crypto.cms.manifest.{ManifestCms, ManifestCmsParser}
import net.ripe.rpki.commons.crypto.cms.roa.RoaCms
import net.ripe.rpki.commons.crypto.crl.X509Crl
import net.ripe.rpki.commons.crypto.x509cert.{X509ResourceCertificate, X509ResourceCertificateParser}
import net.ripe.rpki.commons.validation.ValidationResult
import net.ripe.rpki.validator.fetchers._
import net.ripe.rpki.validator.store._
import org.joda.time.Instant

import scala.collection.JavaConversions._


trait Hashing {
  def getHash(bytes: Array[Byte]): Array[Byte] = ManifestCms.hashContents(bytes)

  def stringify(bytes: Array[Byte]) = Option(bytes).map {
    _.map { b => String.format("%02X", new Integer(b & 0xff))}.mkString
  }.getOrElse("")

  def parseBytes(hex: String): Option[Array[Byte]] = try {
    Some(hex.sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte))
  } catch {
    case _: Throwable => None
  }

  def equals(hashA: Array[Byte], hashB: Array[Byte]): Boolean = { hashA.deep == hashB.deep }
}

object RepositoryObject {
  type ROType = RepositoryObject[T] forSome { type T <: net.ripe.rpki.commons.crypto.CertificateRepositoryObject }
}

sealed trait RepositoryObject[T <: net.ripe.rpki.commons.crypto.CertificateRepositoryObject] extends Hashing {

  def url: String

  def aki: Array[Byte]

  def encoded: Array[Byte]

  def hash: Array[Byte] = getHash(encoded)

  def decoded: T

  def validationTime: Option[Instant]

  def downloadTime: Option[Instant]

  def isExpiredOrRevoked = {
    val d = decoded
    d.isPastValidityTime || d.isRevoked
  }
}

trait Parsing {

  def formatFailures(r: ValidationResult) = r.getFailuresForAllLocations.map {
    ch => s"[${ch.getKey}, status = ${ch.getStatus}, params = ${ch.getParams.mkString(" ")}]"
  }.mkString("\n")

  def parseOrReturnBroken[T](url: String, bytes: Array[Byte])(f: => Either[BrokenObject, T]) = try f catch {
    case e: Exception => Left(BrokenObject(url, bytes, e.getMessage))
  }
}

case class BrokenObject(url: String, bytes: Array[Byte], errorMessage: String, downloadTime: Instant = Instant.now) extends Hashing {
  def hash: Array[Byte] = getHash(bytes)
}

object CertificateObject extends Parsing {

  private def makeParser(url: String, bytes: Array[Byte]) = {
    val parser = new X509ResourceCertificateParser
    parser.parse(url, bytes)
    parser
  }

  def parse(url: String, bytes: Array[Byte]) = CertificateObject(url, makeParser(url, bytes).getCertificate)

  def tryParse(url: String, bytes: Array[Byte]) = parseOrReturnBroken(url, bytes) {
    val parser = makeParser(url, bytes)
    if (parser.isSuccess)
      Right(CertificateObject(url, parser.getCertificate))
    else
      Left(BrokenObject(url, bytes, formatFailures(parser.getValidationResult)))
  }
}


object ManifestObject extends Parsing {

  private def makeParser(url: String, bytes: Array[Byte]) = {
    val parser = new ManifestCmsParser
    parser.parse(url, bytes)
    parser
  }

  def parse(url: String, bytes: Array[Byte]) = ManifestObject(url, makeParser(url, bytes).getManifestCms)

  def tryParse(url: String, bytes: Array[Byte]) = {
    val parser = makeParser(url, bytes)
    if (parser.isSuccess)
      Right(ManifestObject(url, parser.getManifestCms))
    else
      Left(BrokenObject(url, bytes, formatFailures(parser.getValidationResult)))
  }
}

object CrlObject extends Parsing {

  def parse(url: String, bytes: Array[Byte]) = CrlObject(url, new X509Crl(bytes))

  def tryParse(url: String, bytes: Array[Byte]) = parseOrReturnBroken(url, bytes) {
    Right(parse(url, bytes))
  }
}

object RoaObject extends Parsing {

  def parse(url: String, bytes: Array[Byte]) = RoaObject(url, RoaCms.parseDerEncoded(bytes))

  def tryParse(url: String, bytes: Array[Byte]) = parseOrReturnBroken(url, bytes) {
    Right(parse(url, bytes))
  }
}

case class CertificateObject(override val url: String,
                             override val decoded: X509ResourceCertificate,
                             override val downloadTime: Option[Instant] = Some(Instant.now()),
                             override val validationTime: Option[Instant] = None) extends RepositoryObject[X509ResourceCertificate] {

  def encoded = decoded.getEncoded
  def aki = decoded.getAuthorityKeyIdentifier
  def ski = decoded.getSubjectKeyIdentifier
}

case class ManifestObject(override val url: String,
                          override val decoded: ManifestCms,
                          override val downloadTime: Option[Instant] = Some(Instant.now()),
                          override val validationTime: Option[Instant] = None) extends RepositoryObject[ManifestCms] {
  def encoded = decoded.getEncoded
  def aki = decoded.getCertificate.getAuthorityKeyIdentifier
}

case class CrlObject(override val url: String,
                     override val decoded: X509Crl,
                     override val downloadTime: Option[Instant] = Some(Instant.now()),
                     override val validationTime: Option[Instant] = None) extends RepositoryObject[X509Crl] {
  def encoded = decoded.getEncoded
  def aki = decoded.getAuthorityKeyIdentifier
}

case class RoaObject(override val url: String,
                     override val decoded: RoaCms,
                     override val downloadTime: Option[Instant] = Some(Instant.now()),
                     override val validationTime: Option[Instant] = None) extends RepositoryObject[RoaCms] {

  def aki = decoded.getCertificate.getAuthorityKeyIdentifier
  def encoded = decoded.getEncoded
}


class RepoFetcher(storage: Storage, httpStore: HttpFetcherStore, config: FetcherConfig) {

  val rsyncUrlPool = scala.collection.mutable.Set[String]()
  val httpUrlPool = scala.collection.mutable.Set[String]()

  /**
   * It's the mapping of the form "localhost:8888/a/b/c =>
   *   [localhost:8888, localhost:8888/a, localhost:8888/a/b, localhost:8888/a/b/c]
   */
  private def chunked(uri: String) =
    uri.split("/").toSeq.foldLeft((Seq[Seq[String]](), Seq[String]())) {
      (accum, ch) => {
        val newLatest = accum._2 :+ ch
        (accum._1 :+ newLatest, newLatest)
      }
    }._1.map {
      _.mkString("","/","/")
    }

  def fetchObject(repoUri: URI): Seq[Fetcher.Error] = {
    val fetcher = repoUri.getScheme match {
      case "rsync" => new RsyncFetcher(config)
      case "http" | "https" => new SingleObjectHttpFetcher(httpStore)
      case _ => throw new Exception(s"No fetcher for the object $repoUri")
    }

    fetch(repoUri, fetcher)
  }

  def fetch(repoUri: URI): Seq[Fetcher.Error] = {
    val fetcher = repoUri.getScheme match {
      case "rsync" => new RsyncFetcher(config)
      case "http" | "https" => new HttpFetcher(httpStore)
      case _ => throw new Exception(s"No fetcher for the uri $repoUri")
    }

    fetch(repoUri, fetcher)
  }

  private def fetch(repoUri: URI, fetcher: Fetcher): Seq[Fetcher.Error] = {
    storage.atomic {
      fetcher.fetchRepo(repoUri, new FetcherListener {
        override def processObject(repoObj: RepositoryObject.ROType) = {
          repoObj match {
            case c: CertificateObject => storage.storeCertificate(c)
            case c: CrlObject => storage.storeCrl(c)
            case c: ManifestObject => storage.storeManifest(c)
            case c: RoaObject => storage.storeRoa(c)
          }
        }

        override def withdraw(url: URI, hash: String) = {
          storage.delete(url.toString, hash)
        }

        override def processBroken(brokenObj: BrokenObject) = {
          storage.storeBroken(brokenObj)
        }
      })
    }
  }
}

object RepoFetcher {
  def apply(storageDirectory: File, config: FetcherConfig) = {
    val path = storageDirectory.getAbsolutePath
    new RepoFetcher(DurableCaches(path, config.taName), HttpFetcherStore(path), config)
  }
  def inMemory(config: FetcherConfig) = {
    val dataSource = DataSources.InMemoryDataSource
    new RepoFetcher(new CacheStore(dataSource, config.taName), new HttpFetcherStore(dataSource), config)
  }
}