package pipelines.mongo

import pipelines.mongo.audit.AuditServiceMongoSpec
import pipelines.mongo.users.{UserRolesServiceSpec, LoginHandlerMongoTest, RefDataMongoSpec, UserRepoMongoSpec}

class PipelinesMongoTest
    extends BasePipelinesMongoSpec
    with AuditServiceMongoSpec
    with LoginHandlerMongoTest
    with MongoConnectSpec
    with RefDataMongoSpec
    with UserRepoMongoSpec
    with UserRolesServiceSpec
    with LowPriorityMongoImplicitsSpec
