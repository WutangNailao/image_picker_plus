// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.imagepicker

class TestCallback : (Result<List<PickedMedia>>) -> Unit {
    val results = mutableListOf<Result<List<PickedMedia>>>()

    override fun invoke(result: Result<List<PickedMedia>>) {
        results.add(result)
    }

    fun assertNoResults() {
        check(results.isEmpty()) { "Expected no callback results, got $results" }
    }

    fun singleResult(): Result<List<PickedMedia>> {
        check(results.size == 1) { "Expected one callback result, got ${results.size}" }
        return results.single()
    }
}
