package pipelines.mongo

import pipelines.mongo.audit.AuditServiceMongoSpec
import pipelines.mongo.users.{UserRolesServiceSpec, LoginHandlerMongoTest, RefDataMongoSpec, UserServiceMongoSpec}

class PipelinesMongoTest
    extends BasePipelinesMongoSpec
    with AuditServiceMongoSpec
    with LoginHandlerMongoTest
    with MongoConnectSpec
    with RefDataMongoSpec
    with UserServiceMongoSpec
    with UserRolesServiceSpec
    with LowPriorityMongoImplicitsSpec
