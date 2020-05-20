import { NativeModules } from 'react-native';

const { RNLinesdk } = NativeModules;
 

/**
 * Logs the init line
 */ 
const init = (lineChennelID) => {
    return RNLinesdk.init(lineChannelID);
}

/**
 * Logs the user.
 */
const login = (permission = ['PROFILE'], botPrompt = 'none') => {
    return RNLinesdk.login(permission, botPrompt);
  };
  
  /**
   * Get the current access token.
   */
  const getAccessToken = () => {
    return RNLinesdk.getAccessToken();
  };
  
  /**
   * Get user profile.
   */
  const getUserProfile = () => {
    return RNLinesdk.getUserProfile();
  };
  
  /**
   * Get user profile.
   */
  const getFriendshipStatus = () => {
    return RNLinesdk.getFriendshipStatus();
  };
  
  /**
   * Logs out the user.
   */
  const logout = () => {
    return RNLinesdk.logout();
  };

  export default { init, login, getAccessToken, getUserProfile, logout, getFriendshipStatus };