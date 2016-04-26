/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.errorprone.bugpatterns;

import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.Category;
import com.google.errorprone.BugPattern.MaturityLevel;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.BugPattern.Suppressibility;
import com.google.errorprone.VisitorState;
import com.google.errorprone.annotations.RestrictedApi;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.NewClassTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;

/**
 * Check for non-whitelisted callers to RestrictedApiChecker.
 */
@BugPattern(
  name = "RestrictedApiChecker",
  summary = " Check for non-whitelisted callers to RestrictedApiChecker.",
  explanation =
      "Calls to APIs marked @RestrictedApi are prohibited without a corresponding whitelist"
          + " annotation.",
  category = Category.ONE_OFF,
  maturity = MaturityLevel.MATURE,
  severity = SeverityLevel.ERROR,
  suppressibility = Suppressibility.UNSUPPRESSIBLE
)
public class RestrictedApiChecker extends BugChecker
    implements MethodInvocationTreeMatcher, NewClassTreeMatcher {

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    return checkRestriction(ASTHelpers.getAnnotation(tree, RestrictedApi.class), tree, state);
  }

  @Override
  public Description matchNewClass(NewClassTree tree, VisitorState state) {
    return checkRestriction(ASTHelpers.getAnnotation(tree, RestrictedApi.class), tree, state);
  }

  private Description checkRestriction(
      @Nullable RestrictedApi restriction, Tree where, VisitorState state) {
    if (restriction == null) {
      return Description.NO_MATCH;
    }
    if (!restriction.allowedOnPath().isEmpty()) {
      JCCompilationUnit compilationUnit = (JCCompilationUnit) state.getPath().getCompilationUnit();
      if (Pattern.matches(
          restriction.allowedOnPath(), compilationUnit.getSourceFile().toUri().getRawPath())) {
        return Description.NO_MATCH;
      }
    }
    boolean warn =
        Matchers.enclosingNode(shouldAllowWithWarning(restriction)).matches(where, state);
    boolean allow = Matchers.enclosingNode(shouldAllow(restriction)).matches(where, state);
    if (warn && allow) {
      // TODO(bangert): Clarify this message if possible.
      return buildDescription(where)
          .setMessage(
              "The Restricted API (["
                  + restriction.checkerName()
                  + "]"
                  + restriction.explanation()
                  + ") call here is both whitelisted-as-warning and "
                  + "silently whitelisted. "
                  + "Please remove one of the conflicting suppression annotations.")
          .build();
    }
    if (allow) {
      return Description.NO_MATCH;
    }
    SeverityLevel level = warn ? SeverityLevel.WARNING : SeverityLevel.ERROR;

    return Description.builder(
            where, restriction.checkerName(), restriction.link(), level, restriction.explanation())
        .build();
  }

  // TODO(bangert): Memoize these if necessary.
  private static Matcher<Tree> shouldAllow(RestrictedApi api) {
    try {
      return anyAnnotation(api.whitelistAnnotations());
    } catch (MirroredTypesException e) {
      return anyAnnotation(e.getTypeMirrors());
    }
  }

  private static Matcher<Tree> shouldAllowWithWarning(RestrictedApi api) {
    try {
      return anyAnnotation(api.whitelistWithWarningAnnotations());
    } catch (MirroredTypesException e) {
      return anyAnnotation(e.getTypeMirrors());
    }
  }

  private static Matcher<Tree> anyAnnotation(Class<? extends Annotation> annotations[]) {
    ArrayList<Matcher<Tree>> matchers = new ArrayList<>(annotations.length);
    for (Class<? extends Annotation> annotation : annotations) {
      matchers.add(Matchers.hasAnnotation(annotation));
    }
    return Matchers.anyOf(matchers);
  }

  private static Matcher<Tree> anyAnnotation(List<? extends TypeMirror> mirrors) {
    ArrayList<Matcher<Tree>> matchers = new ArrayList<>(mirrors.size());
    for (TypeMirror mirror : mirrors) {
      matchers.add(Matchers.hasAnnotation(mirror.toString()));
    }
    return Matchers.anyOf(matchers);
  }
}
