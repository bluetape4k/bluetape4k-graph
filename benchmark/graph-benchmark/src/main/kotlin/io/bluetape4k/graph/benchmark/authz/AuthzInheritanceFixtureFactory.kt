package io.bluetape4k.graph.benchmark.authz

object AuthzInheritanceFixtureFactory {

    fun create(
        size: AuthzInheritanceSize,
        scenario: AuthzInheritanceScenario = AuthzInheritanceScenario.DEEP_INHERITANCE,
    ): AuthzInheritanceFixture {
        val users = (0 until size.userCount).map { AuthzNode(userId(it), AuthzNodeKind.USER, publicApi = false) }
        val groups = (0 until size.groupCount).map { AuthzNode(groupId(it), AuthzNodeKind.GROUP, publicApi = false) }
        val roles = (0 until size.roleCount).map { AuthzNode(roleId(it), AuthzNodeKind.ROLE, publicApi = false) }
        val resources = (0 until size.resourceCount).map { AuthzNode(resourceId(it), AuthzNodeKind.RESOURCE, publicApi = it % 3 != 0) }
        val nodes = users + groups + roles + resources
        val edges = buildEdges(size, scenario)

        return AuthzInheritanceFixture(
            size = size,
            scenario = scenario,
            targetUserId = users.first().nodeId,
            nodes = nodes,
            edges = edges.distinct(),
        )
    }

    private fun buildEdges(
        size: AuthzInheritanceSize,
        scenario: AuthzInheritanceScenario,
    ): List<AuthzEdge> {
        val edges = ArrayList<AuthzEdge>(size.groupCount * scenario.groupFanout)

        repeat(size.userCount) { user ->
            repeat(scenario.groupFanout) { offset ->
                val group = (user * scenario.groupFanout + offset) % size.groupCount
                edges += inherit(userId(user), groupId(group), AuthzEdgeKind.MEMBER_OF, user + offset, scenario)
            }
        }

        repeat(size.groupCount) { group ->
            if (scenario.cycleEvery > 0 && group > 0 && group % scenario.cycleEvery == 0) {
                edges += inherit(groupId(group), groupId(group - 1), AuthzEdgeKind.MEMBER_OF, group, scenario)
                edges += inherit(groupId(group - 1), groupId(group), AuthzEdgeKind.MEMBER_OF, group + 1, scenario)
            }
            val nextGroup = group + 1
            if (nextGroup < size.groupCount && group % scenario.backgroundGroupStride == 0) {
                edges += inherit(groupId(group), groupId(nextGroup), AuthzEdgeKind.MEMBER_OF, group + 2, scenario)
            }
            repeat(scenario.roleFanout) { offset ->
                val role = (group * scenario.roleFanout + offset) % size.roleCount
                edges += inherit(groupId(group), roleId(role), AuthzEdgeKind.ASSIGNED_ROLE, group + offset, scenario)
            }
        }

        repeat(size.roleCount) { role ->
            repeat(scenario.resourceFanout) { offset ->
                val resource = (role * scenario.resourceFanout + offset) % size.resourceCount
                edges += AuthzEdge(
                    fromNodeId = roleId(role),
                    toNodeId = resourceId(resource),
                    kind = AuthzEdgeKind.GRANTS,
                    effect = if ((role + offset) % scenario.denyEvery == 0) AuthzEffect.DENY else AuthzEffect.ALLOW,
                    active = (role + offset) % scenario.inactiveEvery != 0,
                )
            }
        }

        edges += buildTargetChain(size, scenario)

        return edges
    }

    private fun buildTargetChain(
        size: AuthzInheritanceSize,
        scenario: AuthzInheritanceScenario,
    ): List<AuthzEdge> {
        val edges = ArrayList<AuthzEdge>(scenario.targetChainLength + scenario.resourceFanout + 2)
        val chainLength = scenario.targetChainLength.coerceAtMost(size.groupCount - 1)

        edges += activeInherit(userId(0), groupId(0), AuthzEdgeKind.MEMBER_OF)
        repeat(chainLength) { group ->
            edges += activeInherit(groupId(group), groupId(group + 1), AuthzEdgeKind.MEMBER_OF)
        }

        val terminalGroup = groupId(chainLength)
        val terminalRole = roleId(size.roleCount - 1)
        edges += activeInherit(terminalGroup, terminalRole, AuthzEdgeKind.ASSIGNED_ROLE)

        repeat(scenario.resourceFanout) { offset ->
            val resourceIndex = size.resourceCount - 1 - offset
            edges += AuthzEdge(
                fromNodeId = terminalRole,
                toNodeId = resourceId(resourceIndex),
                kind = AuthzEdgeKind.GRANTS,
                effect = AuthzEffect.ALLOW,
                active = true,
            )
        }

        return edges
    }

    private fun inherit(
        from: String,
        to: String,
        kind: AuthzEdgeKind,
        seed: Int,
        scenario: AuthzInheritanceScenario,
    ): AuthzEdge =
        AuthzEdge(
            fromNodeId = from,
            toNodeId = to,
            kind = kind,
            effect = AuthzEffect.ALLOW,
            active = seed % scenario.inactiveEvery != 0,
        )

    private fun activeInherit(
        from: String,
        to: String,
        kind: AuthzEdgeKind,
    ): AuthzEdge =
        AuthzEdge(
            fromNodeId = from,
            toNodeId = to,
            kind = kind,
            effect = AuthzEffect.ALLOW,
            active = true,
        )

    internal fun userId(index: Int): String = "user-%06d".format(index)
    internal fun groupId(index: Int): String = "group-%06d".format(index)
    internal fun roleId(index: Int): String = "role-%06d".format(index)
    internal fun resourceId(index: Int): String = "resource-%06d".format(index)
}

object AuthzInheritanceOracle {

    fun resolve(fixture: AuthzInheritanceFixture): Set<String> {
        val nodeById = fixture.nodes.associateBy { it.nodeId }
        val bySource = fixture.edges
            .filter { it.active }
            .groupBy { it.fromNodeId }
        val reachable = linkedSetOf<String>()
        val denied = linkedSetOf<String>()
        var frontier = setOf(fixture.targetUserId)

        repeat(fixture.scenario.hopLimit) {
            val next = linkedSetOf<String>()
            frontier.forEach { nodeId ->
                bySource[nodeId].orEmpty().forEach { edge ->
                    val target = nodeById.getValue(edge.toNodeId)
                    if (target.kind == AuthzNodeKind.RESOURCE && target.publicApi) {
                        when (edge.effect) {
                            AuthzEffect.ALLOW -> reachable += target.nodeId
                            AuthzEffect.DENY -> denied += target.nodeId
                        }
                    } else {
                        next += edge.toNodeId
                    }
                }
            }
            frontier = next - reachable
        }

        return reachable - denied
    }
}
