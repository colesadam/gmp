/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import connectors.DesConnector
import models.{GmpValidateSconResponse, ValidateSconRequest}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories.ValidateSconRepository
import uk.gov.hmrc.play.http.Upstream5xxResponse
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ValidateSconController extends BaseController {

  val desConnector: DesConnector

  val repository : ValidateSconRepository

  def validateScon(userId: String) = Action.async(parse.json) {

    implicit request => {

      withJsonBody[ValidateSconRequest] { validateSconRequest =>

        repository.findByScon(validateSconRequest.scon).flatMap {
          case Some(cr) => Future.successful(Ok(Json.toJson(cr)))
          case None => {
            val result = desConnector.validateScon(userId, validateSconRequest.scon)

            result.map {
              validateResult => {
                Logger.debug(s"[ValidateSconController][validateScon] : $validateResult")
                val transformedResult = GmpValidateSconResponse.createFromValidateSconResponse(validateResult)

                Logger.debug(s"[ValidateSconController][transformedResult] : $transformedResult")
                repository.insertByScon(validateSconRequest.scon, transformedResult)
                Ok(Json.toJson(transformedResult))
              }
            }.recover {
              case e: Upstream5xxResponse if e.upstreamResponseCode == 500 => {
                Logger.debug(s"[ValidateSconController][validateScon][transformedResult][ERROR:500] : ${e.getMessage}")
                InternalServerError(e.getMessage)
              }
            }
          }
        }
      }
    }
  }


}

object ValidateSconController extends ValidateSconController {
  // $COVERAGE-OFF$Trivial and never going to be called by a test that uses it's own object implementation
  override val desConnector = DesConnector

  override val repository = ValidateSconRepository()
  // $COVERAGE-ON$
}
