package pipelines.mongo

import pipelines.audit.mongo.AuditServiceMongoSpec
import pipelines.users.mongo.{LoginHandlerMongoTest, RefDataMongoSpec, UserRepoMongoSpec, UserRolesServiceSpec}

class PipelinesMongoTest
    extends BasePipelinesMongoSpec
    with AuditServiceMongoSpec
    with LoginHandlerMongoTest
    with MongoConnectSpec
    with RefDataMongoSpec
    with UserRepoMongoSpec
    with UserRolesServiceSpec
    with LowPriorityMongoImplicitsSpec
