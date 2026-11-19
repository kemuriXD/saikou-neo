package ani.saikou.neo.phone

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ani.saikou.neo.phone.theme.PhoneColors
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.shared.account.SignInProblem

/** Test tag on 1.4's heading. */
internal const val SIGN_IN_PROBLEM_TITLE = "sign_in_problem_title"

/** 1.3 Signed in: shown once, straight after a new sign-in, with the AniList picture and name and Pair a TV. */
@Composable
internal fun SignedInScreen(
    profile: AniListProfile,
    onPair: () -> Unit,
) {
    PhoneColumn(Modifier.safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Status(
                Modifier.weight(1f),
                stringResource(R.string.signed_in_title, profile.name),
                stringResource(R.string.signed_in_body),
            ) {
                Avatar(profile.name, profile.avatarUrl, size = 96.dp, style = MaterialTheme.typography.displaySmall)
            }
            Actions {
                Button(onClick = onPair, modifier = BIG_BUTTON) { ButtonLabel(R.string.signed_in_pair) }
            }
        }
    }
}

/** 1.4 Sign-in didn't work: what happened and how to fix it, in place of Welcome, with Try again. */
@Composable
internal fun SignInProblemScreen(
    problem: SignInProblem,
    onTryAgain: () -> Unit,
) {
    val words = problem.words
    PhoneColumn(Modifier.safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Status(
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                stringResource(words.title),
                stringResource(words.body),
                titleModifier = Modifier.testTag(SIGN_IN_PROBLEM_TITLE),
            ) {
                Badge(words.icon, PhoneColors.error, PhoneColors.surfaceRaised)
            }
            Actions {
                Button(onClick = onTryAgain, modifier = BIG_BUTTON) {
                    ButtonLabel(R.string.sign_in_try_again, R.drawable.icon_refresh)
                }
            }
        }
    }
}

private class ProblemWords(
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
    @param:DrawableRes val icon: Int = R.drawable.icon_warning,
)

private val SignInProblem.words: ProblemWords
    get() =
        when (this) {
            SignInProblem.NO_NETWORK -> {
                ProblemWords(
                    R.string.sign_in_no_network,
                    R.string.sign_in_no_network_body,
                    R.drawable.icon_wifi_off,
                )
            }

            SignInProblem.EXPIRED -> {
                ProblemWords(R.string.sign_in_expired, R.string.sign_in_expired_body)
            }

            SignInProblem.FAILED -> {
                ProblemWords(R.string.sign_in_failed, R.string.sign_in_failed_body)
            }

            SignInProblem.NOT_SAVED -> {
                ProblemWords(R.string.sign_in_not_saved, R.string.sign_in_not_saved_body)
            }

            SignInProblem.SESSION_ENDED -> {
                ProblemWords(R.string.sign_in_session_ended, R.string.sign_in_session_ended_body)
            }

            SignInProblem.NOT_SET_UP -> {
                ProblemWords(R.string.sign_in_not_set_up, R.string.sign_in_not_set_up_body)
            }
        }
