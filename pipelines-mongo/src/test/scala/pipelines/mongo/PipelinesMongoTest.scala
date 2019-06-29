package pipelines.mongo

import pipelines.mongo.audit.AuditServiceMongoSpec
import pipelines.mongo.users.{AuthenticationServiceSpec, LoginHandlerMongoTest, RefDataMongoSpec, UserServiceMongoSpec}

class PipelinesMongoTest
    extends BasePipelinesMongoSpec
    with LoginHandlerMongoTest
    with MongoConnectSpec
    with AuditServiceMongoSpec
    with RefDataMongoSpec
    with UserServiceMongoSpec
    with AuthenticationServiceSpec
    with LowPriorityMongoImplicitsSpec
