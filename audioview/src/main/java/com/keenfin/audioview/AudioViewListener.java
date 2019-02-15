/*
 *           Copyright © 2018 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.audioview;

public interface AudioViewListener {
    void onPrepared();
    void onCompletion();
}
